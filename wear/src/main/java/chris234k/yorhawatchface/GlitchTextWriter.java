package chris234k.yorhawatchface;

import android.os.Handler;
import android.util.Log;

import java.util.Random;

/**
 * Created by Chris on 6/27/2017.
 */

interface ICompletionCallback {
    void onComplete();
}


public class GlitchTextWriter {
    private boolean mIsAnimating;
    private static final int FRAMES_PER_INDEX = 2;
    private int mTextIndex, mFrameIndex;
    private String mTextContent;
    private StringBuilder mTextValue;
    private Handler mHandler;
    private final Runnable mRunnable;
    private final long mTextDrawRate;

    private ICompletionCallback mCompletionCallback;

    private static final String RANDOM_NUMERIC = "1234567890:";

    public GlitchTextWriter(long textDrawRate) {
        mTextIndex = 0;
        mFrameIndex = 1;
        mTextValue = new StringBuilder();
        mTextDrawRate = textDrawRate;

        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
            // Consecutively display each character in the string
            //
            // For index x
            // Draw correct letters from 0 to x-1
            // Frame 1- display random letter at x
            // Frame 2- display value at index 0 at x
            // Advance index
            //
            // This gives the appearance that index 0 is moving through the string to populate it

                mIsAnimating = true;

            char insertChar;

            if (mFrameIndex < FRAMES_PER_INDEX) {
                // Roll random character to display for current index
                Random r = new Random();
                int randomNum = r.nextInt(RANDOM_NUMERIC.length());
                insertChar = RANDOM_NUMERIC.charAt(randomNum);
            } else {
                insertChar = mTextContent.charAt(0);
            }

            // If the string is long enough to pull a sub string from
            if (mTextIndex > 0) {
                // Pull substring
                String subStr = mTextContent.substring(0, mTextIndex);
                mTextValue.replace(0, mTextValue.length(), subStr);
                // Replace last char in sub w/ random
                mTextValue.setCharAt(mTextIndex - 1, insertChar);
            } else {
                // Just use blank string
                mTextValue.replace(0, mTextValue.length(), "");
            }

//            Log.d("yorhawatchface", mTextIndex + " " + mTextValue);

            if (mFrameIndex == FRAMES_PER_INDEX) {
                mTextIndex++;
                mFrameIndex = 1;
            } else {
                mFrameIndex++;
            }

                if(mTextIndex <= mTextContent.length()) {
                    mHandler.postDelayed(mRunnable, mTextDrawRate);
                } else {
                mIsAnimating = false;
                mCompletionCallback.onComplete();
            }
        }
        };
    }

    public boolean getIsAnimating() {
        return mIsAnimating;
    }

    public String getTextValue() {
        return mTextValue.toString();
    }

    public void animateText(String text, long delayMillis, ICompletionCallback completionCallback) {
        // Don't allow animations to be interrupted, stopAnimation should be called directly.
        if(!mIsAnimating) {
            mTextContent = text;
            mTextValue.replace(0, mTextValue.length(), "");
            mTextIndex = 0;
            mFrameIndex = 1;
            mIsAnimating = true;
            mCompletionCallback = completionCallback;

            mHandler.removeCallbacks(mRunnable);
            mHandler.postDelayed(mRunnable, delayMillis);
        }
    }

    public void stopAnimation() {
        if(mIsAnimating) {
            mHandler.removeCallbacks(mRunnable);
        mIsAnimating = false;
    }
}
}
