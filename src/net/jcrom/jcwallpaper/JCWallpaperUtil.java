 package net.jcrom.jcwallpaper;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Environment;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import java.io.File;
import java.lang.reflect.Method;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class JCWallpaperUtil {
    WallpaperService service;
    JCWallpaperService.JCWallpaperEngine engine;
    Bitmap imageLandscape;
    Bitmap imagePortrait;
    public boolean portraitDifferent;
    public boolean fillPortrait = false;
    public boolean fillLandscape = false;
    public boolean rotate = false;
    boolean compensateForBar = false;
    boolean paramLoaded = false;
    Paint bitmapPaint;
    boolean orientationSet = false;
    int currentOrientation;
    int lastOrientation;
    boolean unloadImages = false;
    Integer density = null;
    boolean mBattery = false;
    boolean mTime = false;
    private SharedPreferences mFullHomePrefs;

    private static final String TAG = "JCWallpaper";

    public JCWallpaperUtil(WallpaperService service, JCWallpaperService.JCWallpaperEngine engine) {
    	this.service = service;
    	this.engine = engine;
        bitmapPaint = new Paint();
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);
        mFullHomePrefs = service.getApplicationContext().getSharedPreferences("full_home", Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE|Context.MODE_MULTI_PROCESS);
        
        try {
           DisplayMetrics metrics = service.getBaseContext().getResources().getDisplayMetrics();
           density = metrics.densityDpi;
        }
        catch(Throwable e) {
           e.printStackTrace();
        }
    }
    
    public void loadParam(boolean force) {
        if(force || !paramLoaded) {
            rotate = true;
            unloadImages = true;
            portraitDifferent = true;            
            fillPortrait = true;
            fillLandscape = true;
            compensateForBar = true;
            paramLoaded = true;
            recycleAllImages(null);
        }
    }
    
    public void loadImages() {
        if(!unloadImages) {
            loadLandscapeImage();
            loadPortraitImage();
        }
    }
    
    public void draw(Canvas canvas) {
        if(canvas.getWidth() != engine.width || canvas.getHeight() != engine.height) {
            engine.postAgain = true;
            return;
        }
        
        int width = canvas.getWidth();
        int height = canvas.getHeight();

		compensateForBar = true;
        
        if(compensateForBar) {
            int fullScreenWidth = -1;
            int fullScreenHeight = -1;
            try {
                WindowManager wm = (WindowManager) service.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();   
                
                try {
                    Method mGetRawH = display.getClass().getMethod("getRawHeight");
                    Method mGetRawW = display.getClass().getMethod("getRawWidth");
                    if(mGetRawH != null && mGetRawW != null) {
                        fullScreenWidth = (Integer) mGetRawW.invoke(display);
                        fullScreenHeight = (Integer) mGetRawH.invoke(display);
                    }
                }
                catch(Throwable t) {
                }
                
                if(fullScreenWidth == -1 || fullScreenHeight == -1) {
                    Point size = new Point();
                    display.getRealSize(size);

                    if(size.x > 0 && size.y > 0) {
                        fullScreenWidth = size.x;
                        fullScreenHeight = size.y;
                    }
                }
            }
            catch(Throwable t) {
                t.printStackTrace();
            }
            
            if(fullScreenWidth <= 0 || fullScreenHeight <= 0) {
                fullScreenWidth = 0;
                fullScreenHeight = 0;
            }
            
            if(fullScreenWidth > 0 && fullScreenHeight > 0) {
                width = fullScreenWidth;
                height = fullScreenHeight;
            }
        }
        canvas.drawRect(0, 0, width, height, engine.background);

        int orientationNow = ((WindowManager) service.getApplication().getSystemService(Service.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
        if(!orientationSet) {
        	currentOrientation = orientationNow;
        	lastOrientation = orientationNow;
        	orientationSet = true;
        }
        
        if(orientationNow != currentOrientation) {
        	lastOrientation = currentOrientation;
        	currentOrientation = orientationNow;
        }
       
        if(engine.screenLocked && engine.hideWhenScreenIsLocked) {
            if(unloadImages) {
                recycleAllImages(null);
            }
        }
        else {
            Bitmap bmp;
            
            if (portraitDifferent && width < height) {
                if(unloadImages) {
                    recycleAllImages(imagePortrait);
                    loadPortraitImage();
                }
                bmp = imagePortrait;
            } else {
                if(unloadImages) {
                    recycleAllImages(imageLandscape);
                    loadLandscapeImage();
                }
                bmp = imageLandscape;
            }
            
            if (bmp != null) {
                float scaleWidth = (float) width / bmp.getWidth();
                float scaleHeight = (float) height / bmp.getHeight();
    
                float scale;
                int orientationType = service.getBaseContext().getResources().getConfiguration().orientation;
                
                if ((orientationType == Configuration.ORIENTATION_PORTRAIT && fillPortrait) ||
                    (orientationType == Configuration.ORIENTATION_LANDSCAPE && fillLandscape)) {
                    scale = Math.max(scaleWidth, scaleHeight);
                } else {
                    scale = Math.min(scaleWidth, scaleHeight);
                }
    
                int destWidth = (int) (bmp.getWidth() * scale);
                int destHeight = (int) (bmp.getHeight() * scale);
    
                int x = 0;
                int y = 0;
    
                x = (width - destWidth) / 2;
                y = (height - destHeight) / 2;
    
                Rect dest = new Rect(x, y, x + destWidth, y + destHeight);
    
                boolean rotated = false;
                if(rotate) {
                    if((width < height && destWidth > destHeight) || (width > height && destHeight > destWidth)) {
                        rotated = true;
                        int rWidth = height;
                        int rHeight = width;
    
                        scaleWidth = (float) rWidth / bmp.getWidth();
                        scaleHeight = (float) rHeight / bmp.getHeight();
            
                        if ((orientationType == Configuration.ORIENTATION_PORTRAIT && fillPortrait) ||
                            (orientationType == Configuration.ORIENTATION_LANDSCAPE && fillLandscape)) {
                            scale = Math.max(scaleWidth, scaleHeight);
                        } else {
                            scale = Math.min(scaleWidth, scaleHeight);
                        }
             
                        destWidth = (int) (bmp.getWidth() * scale);
                        destHeight = (int) (bmp.getHeight() * scale);
            
                        if((lastOrientation == Surface.ROTATION_0 && currentOrientation == Surface.ROTATION_90) ||
                           (lastOrientation == Surface.ROTATION_180 && currentOrientation == Surface.ROTATION_270) ||
                           (lastOrientation == Surface.ROTATION_90 && currentOrientation == Surface.ROTATION_180) ||
                           (lastOrientation == Surface.ROTATION_270 && currentOrientation == Surface.ROTATION_0)
                           ) {
                            canvas.rotate(270);
    
                            y = (rHeight - destHeight) / 2;
                            x = -rWidth + ((rWidth - destWidth) / 2);
                            dest = new Rect(x, y, x + destWidth, y + destHeight);
                            canvas.drawBitmap(bmp, null, dest, bitmapPaint);
                            
                            canvas.rotate(-270);
                        }
                        else {
                            canvas.rotate(90);
    
                            y = -rHeight + ((rHeight - destHeight) / 2);
                            x = (rWidth - destWidth) / 2;
                            dest = new Rect(x, y, x + destWidth, y + destHeight);
                            canvas.drawBitmap(bmp, null, dest, bitmapPaint);
                            
                            canvas.rotate(-90);
                        }
                    }
                }
                
                if(!rotated) {
                    canvas.drawBitmap(bmp, null, dest, bitmapPaint);
                }

            }
        } 
    }
    
    public void drawBlack(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        canvas.drawRect(0, 0, width, height, engine.background);
    }
    
    public Bitmap loadImage(String fileName) {
        StringBuilder builder = new StringBuilder();
        builder.append(Environment.getDataDirectory().toString() + "/theme/wallpaper/");
        builder.append(File.separator);
        builder.append(fileName);
        String filePath = builder.toString();
        return BitmapFactory.decodeFile(filePath);        
    }

    public void setBattery(boolean set) {
        mBattery = set;
        Editor e = mFullHomePrefs.edit();
        e.putBoolean("battery", set);
        e.commit();
    }
    public boolean getBattery() {
        return mBattery;
    }

    public void setTime(boolean set) {
        mTime = set;
        Editor e = mFullHomePrefs.edit();
        e.putBoolean("time", set);
        e.commit();
    }
    public boolean getTime() {
        return mTime;
    }

    public void loadLandscapeImage() {
        if(mBattery) {
            if(mTime) {
                imageLandscape = loadImage("home_wallpaper_time_battery_land.png");
            } else {
                imageLandscape = loadImage("home_wallpaper_battery_land.png");
            }
        } else {
            if(mTime) {
                imageLandscape = loadImage("home_wallpaper_time_land.png");
            } else {
                imageLandscape = loadImage("home_wallpaper_land.png");
            }
        }
        if(null == imageLandscape) {
            imageLandscape = loadImage("home_wallpaper_land.png");
        }
    }

    public void loadPortraitImage() {
        if(mBattery) {
            if(mTime) {
                imagePortrait = loadImage("home_wallpaper_time_battery_port.png");
            } else {
                imagePortrait = loadImage("home_wallpaper_battery_port.png");
            }
        } else {
            if(mTime) {
                imagePortrait = loadImage("home_wallpaper_time_port.png");
            } else {
                imagePortrait = loadImage("home_wallpaper_port.png");
            }
        }
        if(null == imagePortrait) {
            imagePortrait = loadImage("home_wallpaper_port.png");
        }
    }

    public void cleanup() {
        paramLoaded = false;
        orientationSet = false;
        engine = null;
        service = null;
        recycleAllImages(null);
    }
    
    public void recycleAllImages(Bitmap keep) {
        if(keep != imageLandscape) {
        	this.recycleBitmap(imageLandscape);
            imageLandscape = null;
        }
        if(keep != imagePortrait) {
        	this.recycleBitmap(imagePortrait);
            imagePortrait = null;
        }
        System.gc();
    }
    
    public void recycleBitmap(Bitmap image) {
        try {
           if (image != null && !image.isRecycled()) {
              image.recycle();
           }
        } catch (Throwable e) {
           e.printStackTrace();
        }       
     }
}
