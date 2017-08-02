package com.tornaco.xtouch.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
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
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.tornaco.xtouch.R;
import com.tornaco.xtouch.provider.SettingsProvider;

import org.newstand.logger.Logger;

import java.io.File;
import java.util.Observable;
import java.util.Observer;

public class FloatView extends FrameLayout {

    private Rect mRect = new Rect();
    private WindowManager mWm;
    private WindowManager.LayoutParams mLp = new WindowManager.LayoutParams();

    private int mTouchSlop, mSwipeSlop;
    private int mTapDelay;
    private float density = getResources().getDisplayMetrics().density;

    private boolean mEdgeEnabled, mRotate, mHeartBeat;
    private float mAlpha;

    private GestureDetectorCompat mDetectorCompat;
    private Callback mCallback;

    private View mContainerView;
    private ImageView mImageView;

    private Handler mHandler = new Handler();

    private Runnable mSingleTapNotifier = new Runnable() {
        @Override
        public void run() {
            mCallback.onSingleTap();
        }
    };

    private Observer o = new Observer() {
        @Override
        public void update(Observable observable, Object o) {
            if (o == SettingsProvider.Key.EDGE) {
                mEdgeEnabled = SettingsProvider.get().getBoolean(SettingsProvider.Key.EDGE);
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

            if (o == SettingsProvider.Key.CUSTOM_IMAGE) {
                // Apply image.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String customPath = SettingsProvider.get().getString(SettingsProvider.Key.CUSTOM_IMAGE);
                        if (!TextUtils.isEmpty(customPath) && new File(customPath).exists()) {
                            mImageView.setImageBitmap(BitmapFactory.decodeFile(customPath));
                        } else {
                            mImageView.setImageResource(R.mipmap.ic_img_def2);
                        }
                    }
                });
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    dump();
                }
            });
        }
    };

    private void dump() {
        Logger.i(toString());
    }

    @Override
    public String toString() {
        return "FloatView{" +
                "mTouchSlop=" + mTouchSlop +
                ", mSwipeSlop=" + mSwipeSlop +
                ", mEdgeEnabled=" + mEdgeEnabled +
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
        SettingsProvider.get().addObserver(o);

        mCallback = (Callback) context;

        mDetectorCompat = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mHandler.removeCallbacks(mSingleTapNotifier);
                mCallback.onDoubleTap();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                mHandler.removeCallbacks(mSingleTapNotifier);
                mHandler.postDelayed(mSingleTapNotifier, mTapDelay);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                inDragMode = true;
                super.onLongPress(e);
            }

            @Override
            public boolean onFling(MotionEvent me, MotionEvent me2, float velocityX, float velocityY) {

                SwipeDirection swipeDirection = null;

                float y = me.getY() - me2.getY();

                float x = me.getX() - me2.getX();

                float absX = Math.abs(x);
                float absY = Math.abs(y);

                if (absX > absY) {
                    // Check slot.
                    if (absX > mSwipeSlop) {
                        // Check direction.
                        swipeDirection = x > 0 ? SwipeDirection.L : SwipeDirection.R;
                    }
                } else if (absX < absY) {
                    // Check slot.
                    if (absY > mSwipeSlop) {
                        // Check direction.
                        swipeDirection = y > 0 ? SwipeDirection.U : SwipeDirection.D;
                    }
                }

                if (swipeDirection != null) {
                    mCallback.onSwipeDirection(swipeDirection);
                }

                return true;
            }
        });


        View rootView = LayoutInflater.from(context).inflate(getLayoutId(), this);
        mContainerView = rootView.findViewById(R.id.container);
        mContainerView.setAlpha(mAlpha);

        mImageView = rootView.findViewById(R.id.image);
        // Apply image.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String customPath = SettingsProvider.get().getString(SettingsProvider.Key.CUSTOM_IMAGE);
                if (!TextUtils.isEmpty(customPath) && new File(customPath).exists()) {
                    mImageView.setImageBitmap(BitmapFactory.decodeFile(customPath));
                } else {
                    mImageView.setImageResource(R.mipmap.ic_img_def2);
                }
            }
        });

        getWindowVisibleDisplayFrame(mRect);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlop = mTouchSlop * mTouchSlop;
        mSwipeSlop = 50; // FIXME Read from Settings.

        mWm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        mLp.gravity = Gravity.START | Gravity.TOP;
        mLp.format = PixelFormat.RGBA_8888;
        mLp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

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
                        inDragMode = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (inDragMode) {
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
                        if (isDragging) {
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
        if (getParent() == null) {
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

    private void startAlphaBreathAnimation() {
        final ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mContainerView, "alpha", 0.3f, 1f);
        alphaAnimator.setDuration(3000);
        alphaAnimator.setInterpolator(new BraethInterpolator());
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                if (!mHeartBeat) {
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
        try {
            mWm.removeViewImmediate(this);
        } catch (Exception ignored) {

        } finally {
            SettingsProvider.get().deleteObserver(o);
        }
    }

    private boolean isDragging, inDragMode;

    private int dp2px(int dp) {
        return (int) (dp * density);
    }

    public void reposition() {
        if (mLp.x < (mRect.width() - getWidth()) / 2) {
            mLp.x = dp2px(5);
        } else {
            mLp.x = mRect.width() - dp2px(55);
        }
        if (mLp.y < mRect.top) {
            mLp.y = mRect.top;
        }
        mWm.updateViewLayout(this, mLp);
    }

    public void repositionInIme(int screenHeight, int mIMEHeight) {
        mWm.updateViewLayout(this, mLp);
        if (screenHeight - mLp.y <= mIMEHeight) {
            Logger.i("Reposition within IME");
            mLp.y -= (mIMEHeight - (screenHeight - mLp.y) + density * 48);
            mWm.updateViewLayout(this, mLp);
        }
    }

    public enum SwipeDirection {
        L, R, U, D
    }

    public interface Callback {
        void onSingleTap();

        void onDoubleTap();

        void onSwipeDirection(@NonNull SwipeDirection direction);
    }

    public class BraethInterpolator implements TimeInterpolator {
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