package net.jcrom.jcwallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.os.Environment;
import java.util.Properties;
import java.lang.Integer;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Calendar;

public class JCWallpaperService extends WallpaperService {
    public static final boolean debug = false;
    private static final String TAG = "JCWallpaper";

    private static final String THEME_DIRECTORY = "/theme/wallpaper/";
    private static final String CONFIGURATION_FILE = "home_wallpaper.conf";
    private static final String HOME_BATTERY = "home.battery";
    private static final String HOME_TIME1 = "home.time1";
    private static final String HOME_TIME2 = "home.time2";
    
    @Override
    public Engine onCreateEngine() {
        return new JCWallpaperEngine();
    }

    public class JCWallpaperEngine extends Engine {
        boolean postAgain = false;
        private final Handler handler = new Handler();
        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                
                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = null;
                try {
                    wallpaper.loadParam(false);
                    wallpaper.loadImages();
                    if (visible) {
                        canvas = holder.lockCanvas();
                        if (canvas != null) {
                            wallpaper.draw(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
                handler.removeCallbacks(drawRunner);
                
            	if(postAgain) {
            	    postAgain = false;
                    handler.postDelayed(drawRunner, retryDelay * 100L);
                    retryDelay *= 2;
            	}
            	else {
            		retryDelay = 1;
            	}
            }
        };
        
        int retryDelay = 1;

        boolean hideWhenScreenIsLocked = false;
        boolean differentImageWhenScreenIsLocked = false;
        boolean screenLocked = false;
        
        int width;
        int height;
        
        JCWallpaperUtil wallpaper;
        private boolean visible = false;

        public Paint background;

        private BroadcastReceiver mReceiver = null;
        boolean toggle = false;
        boolean receiver = false;

        private String mFilePath = Environment.getDataDirectory() + THEME_DIRECTORY + CONFIGURATION_FILE;

        String battery_threshold = null;
        String time1_threshold = null;
        String time2_threshold = null;

        public JCWallpaperEngine() {
            wallpaper = new JCWallpaperUtil(JCWallpaperService.this, this);
            background = this.createPaint(0, 0, 0);
            battery_threshold = loadConf(mFilePath, HOME_BATTERY);
            time1_threshold = loadConf(mFilePath, HOME_TIME1);
            time2_threshold = loadConf(mFilePath, HOME_TIME2);
            initialSettings();
        }
        
        public Paint createPaint(int r, int g, int b) {
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setARGB(255, r, g, b);
            return paint;
        }

        private String loadConf(String filePath, String propertyName) {
            String conf = null;
            Properties prop = new Properties();
            try {
                prop.load(new FileInputStream(filePath));
                conf = prop.getProperty(propertyName);
            } catch (IOException e) {
                conf = null;
            }
            return conf;
        }

        private void initialSettings() {
            int battery_level = getBatteryLevel();
            if((null != battery_threshold) && (battery_level < Integer.parseInt(battery_threshold))) {
                wallpaper.setBattery(true);
            } else {
                wallpaper.setBattery(false);
            }

            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if((null != time1_threshold) && (null != time2_threshold)) {
                if((Integer.parseInt(time1_threshold) > hour) || (Integer.parseInt(time2_threshold)) <= hour) {
                    wallpaper.setTime(true);
                } else {
                    wallpaper.setTime(false);
                }
            } else {
                wallpaper.setTime(false);
            }
        }

        public int getBatteryLevel() {
            Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); 
            int level = bat.getIntExtra("level", 0); 
            int scale = bat.getIntExtra("scale", 100); 
            return ((level * 100) / scale);
        }
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);

            mReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context c, Intent in) {
                    String ac = in.getAction();
                    boolean status = false;

                    if (ac.equals(Intent.ACTION_BATTERY_CHANGED)) {
                        int level = in.getIntExtra("level", 0);
                        int scale = in.getIntExtra("scale", 100);
                        int battery_level = ((level * 100) / scale);

                        if((null != battery_threshold) && (battery_level < Integer.parseInt(battery_threshold))) {
                            if(wallpaper.getBattery()) {
                                status = false;
                            } else {
                                status = true;
                            }
                            wallpaper.setBattery(true);
                        } else {
                            if(wallpaper.getBattery()) {
                                status = true;
                            } else {
                                status = false;
                            }
                            wallpaper.setBattery(false);
                        }
                        
                        if(status) {
                            removeAndPost();
                        }

                    } else if (ac.equals(Intent.ACTION_TIME_TICK)) {
                        Calendar cal = Calendar.getInstance();
                        int hour = cal.get(Calendar.HOUR_OF_DAY);

                        if((null != time1_threshold) && (null != time2_threshold)) {
                            if((Integer.parseInt(time1_threshold) > hour) || (Integer.parseInt(time2_threshold)) <= hour) {
                                if(wallpaper.getTime()) {
                                    status = false;
                                } else {
                                    status = true;
                                }
                                wallpaper.setTime(true);
                            } else {
                                if(wallpaper.getTime()) {
                                    status = true;
                                } else {
                                    status = false;
                                }
                                wallpaper.setTime(false);
                            }
                        } else {
                            if(wallpaper.getTime()) {
                                status = true;
                            } else {
                                status = false;
                            }
                            wallpaper.setTime(false);
                        }

                        if(status) {
                            removeAndPost();
                        }

                    }
                }
            };
        }

        public Context getBaseContext() {
            return JCWallpaperService.this.getBaseContext();
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            this.visible = visible;
            removeAndPost();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            if(receiver) {
                unregisterReceiver(mReceiver);
                receiver = false;
            }
           	handler.removeCallbacks(drawRunner);
        }
        
        @Override
		public void onDestroy() {
            visible = false;
            if(receiver) {
                unregisterReceiver(mReceiver);
                receiver = false;
            }
           	wallpaper.cleanup();
           	handler.removeCallbacks(drawRunner);
            super.onDestroy();
		}

        @Override
        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {
            super.onDesiredSizeChanged(desiredWidth, desiredHeight);
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
            removeAndPost();
        }

        public void removeAndPost() {
            handler.removeCallbacks(drawRunner);
            if (visible) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_TIME_TICK);
                if(receiver) {
                    unregisterReceiver(mReceiver);
                    receiver = false;
                }
                registerReceiver(mReceiver, filter);
                receiver = true;
                handler.post(drawRunner);
            } else {
                if(receiver) {
                    unregisterReceiver(mReceiver);
                    receiver = false;
                }
            }
        }
        
    }
}
