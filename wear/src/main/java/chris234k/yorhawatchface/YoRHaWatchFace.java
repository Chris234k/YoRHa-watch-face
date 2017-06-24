/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chris234k.yorhawatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class YoRHaWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
        private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(32);
        private static final long TEXT_DRAW_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(64);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<YoRHaWatchFace.Engine> mWeakReference;

        public EngineHandler(YoRHaWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            YoRHaWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint, mGridPaint, mTimePaint, mDatePaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private int mWidth, mHeight;
        private float mCenterX, mCenterY;
        private int gridWidth = 10, gridHeight = 10;

        private final Rect mTextBounds = new Rect();
        private String mDateStr;

        // Don't want the text adjusting with each number changed
        private boolean isTextCalculated;
        private float mTextY;

        private static final String RANDOM_CHARS = "1234567890:";
        private boolean mIsAnimating, mForceAnimationStart;
        private int mTextWriterIndex;
        private String mTextWriterContent = new String();
        private StringBuilder mTextWriterValue = new StringBuilder();
        private Handler mTextWriterHandler = new Handler();
        private Runnable mTextWriterRunnable = new Runnable() {
            @Override
            public void run() {
                // Consecutively display each character in the string
                // Viewing next letter is a 3 step process
                // 1. Display correct characters from [0, current index]
                // 2. Grab any character from the string and put it at current index
                // 3. Iterate to next letter

                mIsAnimating = true;

                // Roll random character to display for current index
                Random r = new Random();
                int randomNum = r.nextInt(RANDOM_CHARS.length());
                char randomChar = RANDOM_CHARS.charAt(randomNum);

                // If the string is long enough to pull a sub string from
                if(mTextWriterIndex > 0){
                    // Pull substring
                    String subStr = mTextWriterContent.substring(0, mTextWriterIndex);
                    mTextWriterValue = new StringBuilder(subStr);
                    // Replace last char in sub w/ random
                    mTextWriterValue.setCharAt(mTextWriterIndex-1, randomChar);
                }
                else{
                    mTextWriterValue = new StringBuilder(randomChar);
                }

                Log.d("yorhawatchface", mTextWriterIndex + " " + mTextWriterValue);

                mTextWriterIndex++;

                if(mTextWriterIndex <= mTextWriterContent.length()) {
                    mTextWriterHandler.postDelayed(mTextWriterRunnable, TEXT_DRAW_UPDATE_RATE_MS);
                }
                else{
                    mIsAnimating = false;
                }
            }
        };

        public void animateText(String text) {
            mTextWriterContent = text;
            mTextWriterIndex = 0;

            mTextWriterHandler.removeCallbacks(mTextWriterRunnable);
            mTextWriterHandler.postDelayed(mTextWriterRunnable, TEXT_DRAW_UPDATE_RATE_MS);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(YoRHaWatchFace.this)
                    .setShowUnreadCountIndicator(true)
                    .build());
            Resources resources = YoRHaWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background, null));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.text, null));

            mGridPaint = new Paint();
            mGridPaint = createTextPaint(resources.getColor(R.color.grid, null));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.text, null));

            mCalendar = Calendar.getInstance();

            updateDateStr();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Uzumasa.otf"));
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);

            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            YoRHaWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            YoRHaWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = YoRHaWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTimePaint.setTextSize(textSize);
            mDatePaint.setTextSize(textSize * 0.4f);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            if(inAmbientMode){
                mForceAnimationStart = true;
            }
            else{
                // Don't allow animations in ambient mode
                mIsAnimating = false;
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;

            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                // Draw grid
                for (int i = 0; i < mWidth / gridWidth; i++) {
                    canvas.drawLine(i * gridWidth, 0, i * gridHeight, mHeight, mGridPaint);
                }
                for (int i = 0; i < mHeight / gridHeight; i++) {
                    canvas.drawLine(0, i * gridHeight, mWidth, i * gridHeight, mGridPaint);
                }

                updateDateStr();
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String timeString = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            float yPos = updateTextY(timeString);

            if(!isInAmbientMode()) {
                // On remainder 9 we start the animation (so that it will start on 00) -- doesn't quite work
                if (mForceAnimationStart || mCalendar.get(Calendar.SECOND) % 10 == 9) {
                    mForceAnimationStart = false;
                    animateText(timeString);
                }

                // Draw text using animated text values
                if(mIsAnimating){
                    canvas.drawText(mTextWriterValue.toString(), mCenterX, yPos, mTimePaint);
                }
            }

            if (!mIsAnimating){
                canvas.drawText(timeString, mCenterX, yPos, mTimePaint);
            }

            canvas.drawText(mDateStr, mCenterX, yPos + mHeight * 0.1f, mDatePaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void updateDateStr() {
            mDateStr = DateFormat.format("EEE d", mCalendar).toString().toUpperCase();
        }

        private float updateTextY(String text) {

            // https://stackoverflow.com/a/24969713
            // Draw text centered vertically (accounts for any vertical text pivot variation)
            // Pre calc x and y pos of text (also prevents height variation based on text contents)
            if (!isTextCalculated) {
                isTextCalculated = true;

                mTimePaint.getTextBounds(text, 0, text.length(), mTextBounds);
                mTextY = mCenterY - mTextBounds.exactCenterY();
            }

            return mTextY;
        }
    }
}