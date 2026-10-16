package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * "Öndeki araç kalktı" uyarısı.
 * - Hız, Yandex'in zaten aldığı konumdan (pasif konum) okunur: ek GPS/pil harcamaz.
 * - Araç 3 sn durunca arka kamera düşük çözünürlükte açılır, öndeki görüntü saniyede ~4 kez karşılaştırılır.
 * - Ortadaki bölge (öndeki araç) belirgin şekilde değişince kısa bir "dıt dıt" çalınır.
 * - Hareket edince, uygulama arka plana geçince ya da telefon çok ısınınca kamera kapanır.
 */
final class FrontCarWatcher {
    private static final long STOP_DELAY = 3000;      // bu kadar duruşta kamera açılır
    private static final long SETTLE = 1500;          // pozlama otursun
    private static final long FRAME_EVERY = 250;      // ~4 kare/sn
    private static final float MOVING_KMH = 4f;
    private static final int GRID_W = 32, GRID_H = 24;

    private final Activity a;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread camThread;
    private Handler cam;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;

    private boolean running, camOn, alerted;
    private long stoppedSince = -1, camStartedAt, lastFrame;
    private int[] base;
    private int baseFrames, hits;

    private final LocationListener speed = this::onLocation;
    private final Runnable maybeStart = this::maybeStartCamera;

    FrontCarWatcher(Activity a) { this.a = a; }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE).getBoolean("frontCar", false)
                && c.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (running || !enabled(a)) return;
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        running = true;
        try {
            a.getSystemService(LocationManager.class).requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER, 1000, 0, speed, Looper.getMainLooper());
        } catch (Exception ignored) {
        }
    }

    void stop() {
        running = false;
        main.removeCallbacks(maybeStart);
        try {
            a.getSystemService(LocationManager.class).removeUpdates(speed);
        } catch (Exception ignored) {
        }
        closeCamera();
        stoppedSince = -1;
    }

    private void onLocation(Location l) {
        if (!running || !l.hasSpeed()) return;
        float kmh = l.getSpeed() * 3.6f;
        if (kmh >= MOVING_KMH) {
            // Hareket: kamerayı kapat, sonraki duruşta yeniden uyar
            stoppedSince = -1;
            alerted = false;
            main.removeCallbacks(maybeStart);
            closeCamera();
        } else if (stoppedSince < 0) {
            stoppedSince = System.currentTimeMillis();
            main.removeCallbacks(maybeStart);
            main.postDelayed(maybeStart, STOP_DELAY);
        }
    }

    private void maybeStartCamera() {
        if (!running || camOn || alerted || stoppedSince < 0) return;
        if (tooHot()) return;
        openCamera();
    }

    /** Telefon 43°C üstündeyse kamerayı hiç açma. */
    private boolean tooHot() {
        Intent b = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) > 430;
    }

    // ---------- Kamera ----------

    private void openCamera() {
        try {
            CameraManager cm = a.getSystemService(CameraManager.class);
            String id = null;
            for (String c : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(c).get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) { id = c; break; }
            }
            if (id == null) return;
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size best = new Size(320, 240);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                long bestArea = Long.MAX_VALUE;
                for (Size s : sizes) {
                    long area = (long) s.getWidth() * s.getHeight();
                    if (s.getWidth() >= 240 && area < bestArea) { bestArea = area; best = s; }
                }
            }
            Range<Integer> fps = null;
            Range<Integer>[] ranges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges != null) for (Range<Integer> r : ranges)
                if (fps == null || r.getUpper() < fps.getUpper()) fps = r;
            final Range<Integer> lowFps = fps;

            camThread = new HandlerThread("onArac");
            camThread.start();
            cam = new Handler(camThread.getLooper());
            reader = ImageReader.newInstance(best.getWidth(), best.getHeight(), ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onImage, cam);
            camOn = true;
            camStartedAt = System.currentTimeMillis();
            base = null;
            baseFrames = 0;
            hits = 0;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    device = d;
                    try {
                        d.createCaptureSession(Collections.singletonList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            CaptureRequest.Builder rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            rb.addTarget(reader.getSurface());
                                            if (lowFps != null) rb.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, lowFps);
                                            s.setRepeatingRequest(rb.build(), null, cam);
                                        } catch (Exception e) {
                                            main.post(FrontCarWatcher.this::closeCamera);
                                        }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession s) {
                                        main.post(FrontCarWatcher.this::closeCamera);
                                    }
                                }, cam);
                    } catch (Exception e) {
                        main.post(FrontCarWatcher.this::closeCamera);
                    }
                }
                @Override public void onDisconnected(CameraDevice d) { main.post(FrontCarWatcher.this::closeCamera); }
                @Override public void onError(CameraDevice d, int e) { main.post(FrontCarWatcher.this::closeCamera); }
            }, cam);
        } catch (SecurityException | android.hardware.camera2.CameraAccessException | IllegalArgumentException e) {
            closeCamera();
        }
    }

    private void closeCamera() {
        camOn = false;
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        try { if (device != null) device.close(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        session = null;
        device = null;
        reader = null;
        if (camThread != null) camThread.quitSafely();
        camThread = null;
        cam = null;
    }

    /** Kamera iş parçacığında: küçük bir gri ızgara çıkar, referansla karşılaştır. */
    private void onImage(ImageReader r) {
        Image img;
        try { img = r.acquireLatestImage(); } catch (Exception e) { return; }
        if (img == null) return;
        try {
            long now = System.currentTimeMillis();
            if (now - camStartedAt < SETTLE || now - lastFrame < FRAME_EVERY) return;
            lastFrame = now;
            int[] g = grid(img);
            if (base == null) base = new int[g.length];
            if (baseFrames < 3) { // referans: ilk 3 karenin ortalaması
                for (int i = 0; i < g.length; i++) base[i] += g[i];
                if (++baseFrames == 3) for (int i = 0; i < g.length; i++) base[i] /= 3;
                return;
            }
            // Orta bölge (öndeki araç) ve kenarlar (ışık değişimi) ayrı ölçülür
            long center = 0, edge = 0;
            int nc = 0, ne = 0;
            for (int y = 0; y < GRID_H; y++) for (int x = 0; x < GRID_W; x++) {
                int d = Math.abs(g[y * GRID_W + x] - base[y * GRID_W + x]);
                boolean c = x >= GRID_W * 3 / 10 && x < GRID_W * 7 / 10 && y >= GRID_H * 3 / 10 && y < GRID_H * 8 / 10;
                if (c) { center += d; nc++; } else { edge += d; ne++; }
            }
            float dc = center / (float) nc, de = edge / (float) Math.max(1, ne);
            // Sadece orta bölge belirgin değiştiyse (bütün sahne değil) say
            if (dc > 18 && dc > de * 1.8f) hits++; else hits = 0;
            if (hits >= 3) main.post(this::alert);
        } finally {
            img.close();
        }
    }

    private static int[] grid(Image img) {
        Image.Plane p = img.getPlanes()[0];
        ByteBuffer buf = p.getBuffer();
        int rs = p.getRowStride(), ps = p.getPixelStride();
        int w = img.getWidth(), h = img.getHeight();
        int[] g = new int[GRID_W * GRID_H];
        for (int gy = 0; gy < GRID_H; gy++) {
            int y = gy * h / GRID_H + h / (GRID_H * 2);
            for (int gx = 0; gx < GRID_W; gx++) {
                int x = gx * w / GRID_W + w / (GRID_W * 2);
                g[gy * GRID_W + gx] = buf.get(y * rs + x * ps) & 0xFF;
            }
        }
        return g;
    }

    private void alert() {
        if (alerted || !camOn) return;
        alerted = true;
        closeCamera(); // bu duruş için iş bitti
        try {
            ToneGenerator t = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            t.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
            main.postDelayed(t::release, 800);
        } catch (Exception ignored) {
        }
        Toast.makeText(a, "🚗 Öndeki araç hareket etti", Toast.LENGTH_SHORT).show();
    }
}
