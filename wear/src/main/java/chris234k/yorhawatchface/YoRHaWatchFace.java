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

public class YoRHaWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode.
     */
        private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(33);
        private static final long TEXT_DRAW_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(33);

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

        // Text animation
        private GlitchTextWriter mGlitchWriter;
        private boolean mForceAnimationStart;
        // Don't want the text adjusting with each number changed
        private boolean isTextCalculated;
        private float mTextY;

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

            mGlitchWriter = new GlitchTextWriter(TEXT_DRAW_UPDATE_RATE_MS);

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
            paint.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/FOT-RodinBokutoh Pro M.otf"));
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
                mGlitchWriter.stopAnimation();
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
                    if(i % 5 == 0 || i % 5 == 1){
                        mGridPaint.setStrokeWidth(3);
                    } else{
                        mGridPaint.setStrokeWidth(2);
                    }

                    canvas.drawLine(i * gridWidth, 0, i * gridHeight, mHeight, mGridPaint);
                }

                for (int i = 0; i < mHeight / gridHeight; i++) {
                    if(i % 5 == 0 || i % 5 == 1){
                        mGridPaint.setStrokeWidth(3);
                    } else{
                        mGridPaint.setStrokeWidth(2);
                    }

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
                    mGlitchWriter.animateText(timeString);
                }

                // Draw text using animated text values
                if(mGlitchWriter.getIsAnimating()){
                    canvas.drawText(mGlitchWriter.getTextValue(), mCenterX, yPos, mTimePaint);
                }
            }

            if (!mGlitchWriter.getIsAnimating()){
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