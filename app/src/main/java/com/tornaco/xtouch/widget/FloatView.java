package com.tornaco.xtouch.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.tornaco.xtouch.R;
import com.tornaco.xtouch.provider.SettingsProvider;
import com.tornaco.xtouch.service.GestureDetectorCompat;

import org.newstand.logger.Logger;

import java.io.File;
import java.util.Observable;
import java.util.Observer;

import dev.tornaco.vangogh.Vangogh;
import dev.tornaco.vangogh.display.BlurEffect;
import dev.tornaco.vangogh.display.CircleImageEffect;
import dev.tornaco.vangogh.display.ImageEffect;
import dev.tornaco.vangogh.display.appliers.FadeInApplier;

public class FloatView extends FrameLayout {

    private Rect mRect = new Rect();
    private WindowManager mWm;
    private WindowManager.LayoutParams mLp = new WindowManager.LayoutParams();

    private int mTouchSlop, mSwipeSlop;
    private int mLargeSlop;
    private int mSize;
    private int mScreenHeight, mScreenWidth;
    private int mTapDelay;
    private float density = getResources().getDisplayMetrics().density;

    private boolean mDoubleTapEnabled, mEdgeEnabled, mRotate, mHeartBeat, mFeedbackAnimEnabled, mLocked;
    private float mAlpha;

    private GestureDetectorCompat mDetectorCompat;
    private Callback mCallback;

    private View mContainerView;
    private ImageView mImageView;

    private boolean mScreenOff;

    private Handler mHandler = new Handler();

    private Runnable mSingleTapNotifier = new Runnable() {
        @Override
        public void run() {
            mCallback.onSingleTap();
            performTapFeedbackIfNeed();
        }
    };

    private Observer o = new Observer() {
        @Override
        public void update(Observable observable, Object o) {
            if (o == SettingsProvider.Key.EDGE) {
                mEdgeEnabled = SettingsProvider.get().getBoolean(SettingsProvider.Key.EDGE);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mEdgeEnabled) {
                            reposition();
                        }
                    }
                });
            }
            if (o == SettingsProvider.Key.ALPHA) {
                mAlpha = (float) SettingsProvider.get().getInt(SettingsProvider.Key.ALPHA) / (float) 100;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isAttachedToWindow()) mContainerView.setAlpha(mAlpha);
                    }
                });
            }
            if (o == SettingsProvider.Key.ROTATE) {
                mRotate = SettingsProvider.get().getBoolean(SettingsProvider.Key.ROTATE);
                cleanAnim();
                applyAnim();
            }
            if (o == SettingsProvider.Key.HEART_BEAT) {
                mHeartBeat = SettingsProvider.get().getBoolean(SettingsProvider.Key.HEART_BEAT);
                cleanAnim();
                applyAnim();
            }
            if (o == SettingsProvider.Key.TAP_DELAY) {
                mTapDelay = SettingsProvider.get().getInt(SettingsProvider.Key.TAP_DELAY);
            }

            if (o == SettingsProvider.Key.CUSTOM_IMAGE
                    || o == SettingsProvider.Key.BLUR
                    || o == SettingsProvider.Key.CROP_TO_CIRCLE) {
                applyImage();
            }

            if (o == SettingsProvider.Key.SIZE) {
                mSize = SettingsProvider.get().getInt(SettingsProvider.Key.SIZE);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPostSizeChange();
                    }
                });
            }

            if (o == SettingsProvider.Key.FEEDBACK_ANIM) {
                mFeedbackAnimEnabled = SettingsProvider.get().getBoolean(SettingsProvider.Key.FEEDBACK_ANIM);
            }

            if (o == SettingsProvider.Key.LARGE_SLOP) {
                mLargeSlop = SettingsProvider.get().getInt(SettingsProvider.Key.LARGE_SLOP);
            }

            if (o == SettingsProvider.Key.LOCKED) {
                mLocked = SettingsProvider.get().getBoolean(SettingsProvider.Key.LOCKED);
            }
        }
    };

    private void applyImage() {
        // Apply image.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String customPath = SettingsProvider.get().getString(SettingsProvider.Key.CUSTOM_IMAGE);
                Logger.i("Applying custom image: %s", customPath);
                if (!TextUtils.isEmpty(customPath) && new File(customPath).exists()) {
                    boolean cropToCircle = SettingsProvider.get().getBoolean(SettingsProvider.Key.CROP_TO_CIRCLE);
                    boolean blur = SettingsProvider.get().getBoolean(SettingsProvider.Key.BLUR);
                    ImageEffect[] effects = null;
                    if (cropToCircle && blur) {
                        effects = new ImageEffect[]{new BlurEffect(), new CircleImageEffect()};
                    } else if (cropToCircle) {
                        effects = new ImageEffect[]{new CircleImageEffect()};
                    } else if (blur) {
                        effects = new ImageEffect[]{new BlurEffect()};
                    }
                    Vangogh.from(getContext())
                            .load(customPath)
                            .skipDiskCache(true)
                            .skipMemoryCache(true)
                            .applier(new FadeInApplier())
                            .effect(effects)
                            .into(mImageView);
                } else {
                    mImageView.setImageResource(R.mipmap.ic_def_img);
                }
            }
        });
    }

    private void onPostSizeChange() {
        if (!isAttachedToWindow()) return;
        mLp.width = dp2px(mSize);
        mLp.height = dp2px(mSize);
        mWm.updateViewLayout(this, mLp);
    }

    public void setDoubleTapEnabled(boolean doubleTapEnabled) {
        this.mDoubleTapEnabled = doubleTapEnabled;
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        if (screenState == SCREEN_STATE_OFF) {
            cleanAnim();
            mScreenOff = true;
        } else {
            mScreenOff = false;
            applyAnim();
        }
    }

    private void dump() {
        Logger.i(toString());
    }

    @Override
    public String toString() {
        return "FloatView{" +
                "mTouchSlop=" + mTouchSlop +
                ", mSwipeSlop=" + mSwipeSlop +
                ", mSize=" + mSize +
                ", mTapDelay=" + mTapDelay +
                ", density=" + density +
                ", mRotate=" + mRotate +
                ", mHeartBeat=" + mHeartBeat +
                ", mAlpha=" + mAlpha +
                ", isDragging=" + isDragging +
                ", inDragMode=" + inDragMode +
                '}';
    }

    public FloatView(final Context context) {
        super(context);

        // Read settings.
        mEdgeEnabled = SettingsProvider.get().getBoolean(SettingsProvider.Key.EDGE);
        mRotate = SettingsProvider.get().getBoolean(SettingsProvider.Key.ROTATE);
        mHeartBeat = SettingsProvider.get().getBoolean(SettingsProvider.Key.HEART_BEAT);
        mAlpha = (float) SettingsProvider.get().getInt(SettingsProvider.Key.ALPHA) / (float) 100;
        mTapDelay = SettingsProvider.get().getInt(SettingsProvider.Key.TAP_DELAY);
        mSize = SettingsProvider.get().getInt(SettingsProvider.Key.SIZE);
        mLargeSlop = SettingsProvider.get().getInt(SettingsProvider.Key.LARGE_SLOP);
        mFeedbackAnimEnabled = SettingsProvider.get().getBoolean(SettingsProvider.Key.FEEDBACK_ANIM);
        mLocked = SettingsProvider.get().getBoolean(SettingsProvider.Key.LOCKED);

        SettingsProvider.get().addObserver(o);

        mCallback = (Callback) context;

        mDetectorCompat = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mHandler.removeCallbacks(mSingleTapNotifier);
                mCallback.onDoubleTap();
                performTapFeedbackIfNeed();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (mDoubleTapEnabled) {
                    mHandler.removeCallbacks(mSingleTapNotifier);
                    mHandler.postDelayed(mSingleTapNotifier, mTapDelay);
                } else {
                    mSingleTapNotifier.run();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                inDragMode = true;
                super.onLongPress(e);
                mCallback.onLongPress();
                performTapFeedbackIfNeed();
            }

            @Override
            public boolean onFling(MotionEvent me, MotionEvent me2, float velocityX, float velocityY) {

                SwipeDirection swipeDirection = null;

                float y = me.getY() - me2.getY();

                float x = me.getX() - me2.getX();

                float absX = Math.abs(x);
                float absY = Math.abs(y);

                boolean large = false;

                if (absX > absY) {
                    // Check slot.
                    if (absX > mSwipeSlop) {
                        // Check direction.
                        swipeDirection = x > 0 ? SwipeDirection.L : SwipeDirection.R;
                        int largeThs = (int) ((float) mLargeSlop / (float) 100 * (float) mScreenWidth);
                        large = absX >= largeThs;
                    }
                } else if (absX < absY) {
                    // Check slot.
                    if (absY > mSwipeSlop) {
                        // Check direction.
                        swipeDirection = y > 0 ? SwipeDirection.U : SwipeDirection.D;
                        int largeThs = (int) ((float) mLargeSlop / (float) 100 * (float) mScreenHeight);
                        large = absY >= largeThs;
                    }
                }

                if (swipeDirection != null) {
                    if (large) {
                        mCallback.onSwipeDirectionLargeDistance(swipeDirection);
                    } else {
                        mCallback.onSwipeDirection(swipeDirection);
                    }
                    performTapFeedbackIfNeed();
                }

                return true;
            }
        });


        View rootView = LayoutInflater.from(context).inflate(getLayoutId(), this);
        mContainerView = rootView.findViewById(R.id.container);
        mContainerView.setAlpha(mAlpha);

        mImageView = rootView.findViewById(R.id.image);
        applyImage();

        getWindowVisibleDisplayFrame(mRect);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlop = mTouchSlop * mTouchSlop;
        mSwipeSlop = 50; // FIXME Read from Settings.

        mWm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);

        mScreenHeight = mWm.getDefaultDisplay().getHeight();
        mScreenWidth = mWm.getDefaultDisplay().getWidth();

        mLp.gravity = Gravity.START | Gravity.TOP;
        mLp.format = PixelFormat.RGBA_8888;
        mLp.width = dp2px(mSize);
        mLp.height = dp2px(mSize);
        mLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLp.type = Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        Logger.i("mLp.type: %s", mLp.type);

        OnTouchListener touchListener = new OnTouchListener() {
            private float touchX;
            private float touchY;
            private float startX;
            private float startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchX = event.getX() + getLeft();
                        touchY = event.getY() + getTop();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        isDragging = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!mLocked && inDragMode) {
                            int dx = (int) (event.getRawX() - startX);
                            int dy = (int) (event.getRawY() - startY);
                            if ((dx * dx + dy * dy) > mTouchSlop) {
                                isDragging = true;
                                mLp.x = (int) (event.getRawX() - touchX);
                                mLp.y = (int) (event.getRawY() - touchY);
                                mWm.updateViewLayout(FloatView.this, mLp);
                                return true;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        touchX = touchY = 0.0F;
                        if (!mLocked && isDragging) {
                            if (mEdgeEnabled) {
                                reposition();
                            }
                            isDragging = false;
                            inDragMode = false;
                            return true;
                        }
                }
                return mDetectorCompat.onTouchEvent(event);
            }
        };
        setOnTouchListener(touchListener);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                cleanAnim();
                applyAnim();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {

            }
        });
    }

    protected int getLayoutId() {
        return R.layout.float_controls_circle;
    }

    public void attach() {
        Logger.i("attach");
        if (getParent() == null) {
            Logger.i("attach.add %s", mLp);
            mWm.addView(this, mLp);
        }
        mWm.updateViewLayout(this, mLp);
        getWindowVisibleDisplayFrame(mRect);
        mRect.top += dp2px(50);
        mLp.y = dp2px(150);
        mLp.x = mRect.width() - dp2px(55);
        reposition();
    }

    private void applyAnim() {
        if (mRotate) {
            Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.rotate);
            animation.setInterpolator(getContext(), android.R.anim.linear_interpolator);
            mContainerView.startAnimation(animation);
            Logger.i("applyAnim: Rotate");
        }
        if (mHeartBeat) {
            startAlphaBreathAnimation();
        }
    }

    private void performTapFeedbackIfNeed() {
        if (!mFeedbackAnimEnabled) return;
        AnimatorSet set = new AnimatorSet();
        final ObjectAnimator alphaAnimatorX = ObjectAnimator.ofFloat(mContainerView, "scaleX", 1f, 0.8f);
        final ObjectAnimator alphaAnimatorY = ObjectAnimator.ofFloat(mContainerView, "scaleY", 1f, 0.8f);
        set.setDuration(120);
        set.setInterpolator(new LinearInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                AnimatorSet set = new AnimatorSet();
                final ObjectAnimator alphaAnimatorX = ObjectAnimator.ofFloat(mContainerView, "scaleX", 0.8f, 1f);
                final ObjectAnimator alphaAnimatorY = ObjectAnimator.ofFloat(mContainerView, "scaleY", 0.8f, 1f);
                set.setDuration(120);
                set.setInterpolator(new LinearInterpolator());
                set.playTogether(alphaAnimatorX, alphaAnimatorY);
                set.start();
            }
        });
        set.playTogether(alphaAnimatorX, alphaAnimatorY);
        set.start();
    }

    private void startAlphaBreathAnimation() {
        final ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mContainerView, "alpha", 0.3f, 1f);
        alphaAnimator.setDuration(6000);
        alphaAnimator.setInterpolator(new BreathInterpolator());
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                super.onAnimationRepeat(animation);
                if (!mHeartBeat || mScreenOff) {
                    alphaAnimator.cancel();
                }
            }
        });
        alphaAnimator.start();
    }

    private void cleanAnim() {
        mContainerView.clearAnimation();
    }

    public void detach() {
        Logger.i("detach, current is attached?: %s", isAttachedToWindow());
        try {
            mWm.removeViewImmediate(this);
        } catch (Exception ignored) {

        } finally {
            SettingsProvider.get().deleteObserver(o);
        }
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void hide() {
        if (!isShowing()) return;
        AnimatorSet set = new AnimatorSet();
        final ObjectAnimator alphaAnimatorX = ObjectAnimator.ofFloat(mContainerView, "scaleX", 1f, 0f);
        final ObjectAnimator alphaAnimatorY = ObjectAnimator.ofFloat(mContainerView, "scaleY", 1f, 0f);
        set.setDuration(300);
        set.setInterpolator(new LinearInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setVisibility(INVISIBLE);
            }
        });
        set.playTogether(alphaAnimatorX, alphaAnimatorY);
        set.start();
    }

    public void show() {
        Logger.i("Show: isShowing: %s", isDragging);
        if (isShowing()) return;
        setVisibility(VISIBLE);
        AnimatorSet set = new AnimatorSet();
        final ObjectAnimator alphaAnimatorX = ObjectAnimator.ofFloat(mContainerView, "scaleX", 0f, 1f);
        final ObjectAnimator alphaAnimatorY = ObjectAnimator.ofFloat(mContainerView, "scaleY", 0f, 1f);
        set.setDuration(300);
        set.setInterpolator(new LinearInterpolator());
        set.playTogether(alphaAnimatorX, alphaAnimatorY);
        set.start();
    }

    private boolean isDragging, inDragMode;

    private int dp2px(int dp) {
        return (int) (dp * density);
    }

    public void refreshRect() {
        getWindowVisibleDisplayFrame(mRect);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public void exchangeXY() {
        int x = mLp.x;
        mLp.x = mLp.y;
        mLp.y = x;
        mWm.updateViewLayout(this, mLp);
    }

    public void reposition() {
        if (mLp.x < (mRect.width() - getWidth()) / 2) {
            mLp.x = 0;
        } else {
            mLp.x = mRect.width() - dp2px(mSize);
        }
        if (mLp.y < mRect.top) {
            mLp.y = mRect.top;
        }

        mWm.updateViewLayout(this, mLp);
    }

    private int previousX, previousY;
    private boolean needRestoreOnImeHidden;

    public void restoreXYOnImeHiddenIfNeed() {
        if (needRestoreOnImeHidden) {
            Logger.i("restoreXYOnImeHidden to: %s, %s", previousX, previousY);
            mLp.x = previousX;
            mLp.y = previousY;
            mWm.updateViewLayout(this, mLp);
            needRestoreOnImeHidden = false;
        }
    }

    public void repositionInIme(int screenHeight, int mIMEHeight) {
        if (screenHeight - mLp.y <= mIMEHeight) {
            previousX = mLp.x;
            previousY = mLp.y;
            Logger.i("Reposition within IME");
            mLp.y -= (mIMEHeight - (screenHeight - mLp.y) + density * 48 * 2 /*Nav and candidate*/);
            mWm.updateViewLayout(this, mLp);
            needRestoreOnImeHidden = true;
        }
    }

    public enum SwipeDirection {
        L, R, U, D
    }

    public interface Callback {
        void onSingleTap();

        void onDoubleTap();

        void onSwipeDirection(@NonNull SwipeDirection direction);

        void onSwipeDirectionLargeDistance(@NonNull SwipeDirection direction);

        void onLongPress();
    }

    private class BreathInterpolator implements TimeInterpolator {
        @Override
        public float getInterpolation(float input) {

            float x = 6 * input;
            float k = 1.0f / 3;
            int t = 6;
            int n = 1;
            float PI = 3.1416f;
            float output = 0;

            if (x >= ((n - 1) * t) && x < ((n - (1 - k)) * t)) {
                output = (float) (0.5 * Math.sin((PI / (k * t)) * ((x - k * t / 2) - (n - 1) * t)) + 0.5);

            } else if (x >= (n - (1 - k)) * t && x < n * t) {
                output = (float) Math.pow((0.5 * Math.sin((PI / ((1 - k) * t)) * ((x - (3 - k) * t / 2) - (n - 1) * t)) + 0.5), 2);
            }
            return output;
        }
    }
}