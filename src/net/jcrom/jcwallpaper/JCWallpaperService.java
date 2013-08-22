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

public class JCWallpaperService extends WallpaperService {
    public static final boolean debug = false;
    
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

        public JCWallpaperEngine() {
            wallpaper = new JCWallpaperUtil(JCWallpaperService.this, this);
            background = this.createPaint(0, 0, 0);
        }
        
        public Paint createPaint(int r, int g, int b) {
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setARGB(255, r, g, b);
            return paint;
        }
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
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
           	handler.removeCallbacks(drawRunner);
        }
        
        @Override
		public void onDestroy() {
            visible = false;
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
                handler.post(drawRunner);
            }
        }
        
    }
}
