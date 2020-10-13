package com.example.instagramscrollingindicator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by longlk on 12/10/2020
 *
 * <h3>Glossary of terms:</h3>
 *
 * <ul>
 *     <li><em>S:</em> Dot type small {@link DotType#SMALL}.</li>
 *     <li><em>M:</em> Dot type small {@link DotType#MEDIUM}.</li>
 *     <li><em>N:</em> Dot type small {@link DotType#NORMAL}.</li>
 *     <li><em>SEL:</em> Dot type small {@link DotType#SELECTED}.</li>
 *     <li><em>NO:</em> Dot type small {@link DotType#NONE}.</li>
 * </ul>
 *
 * <em>SEL</em> can sometimes refer to <em>N</em>
 */
public class ScrollingPagerIndicator extends View {

    public static final int NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS = 5;
    /* Include M and N type */
    public static final int NUMBER_OF_SMALL_SIZE_DOTS = 2;
    public static final int MAX_NUMBER_OF_DOTS = 9;

    private int preMeasureWidth = 0;
    private int preMeasureHeight = 0;
    private final int mSpacing;
    private final float mDotSmallRadius;
    private final float mDotMediumRadius;
    private final float mDotNormalRadius;
    private final float mDotSelectedRadius;
    private final int spaceBetweenDotCenters;
    private int minVisibleDotCount = 2;

    private int mPageIndex = 0;
    private int mSelectedDotIndex = 0;

    private int mItemCount;
    private List<DotHolder> mDotHolders = new ArrayList<>(0);
    private boolean dotCountInitialized;

    private final Paint paint;

    @ColorInt
    private int mDotColor;
    @ColorInt
    private int mSelectedDotColor;

    private Runnable attachRunnable;
    private PagerAttacher<?> currentAttacher;

    public ScrollingPagerIndicator(Context context) {
        this(context, null);
    }

    public ScrollingPagerIndicator(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollingPagerIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ScrollingPagerIndicator);
        mDotColor = attributes.getColor(R.styleable.ScrollingPagerIndicator_spi_dotColor, 0);
        mSelectedDotColor = attributes.getColor(R.styleable.ScrollingPagerIndicator_spi_dotSelectedColor, mDotColor);

        float dotSmallSize = attributes.getDimension(R.styleable.ScrollingPagerIndicator_spi_dotSmallRadius, 0);
        float dotMediumSize = attributes.getDimension(R.styleable.ScrollingPagerIndicator_spi_dotMediumRadius, 0);
        float dotNormalSize = attributes.getDimension(R.styleable.ScrollingPagerIndicator_spi_dotNormalRadius, 0);
        float dotSelectedSize = attributes.getDimension(R.styleable.ScrollingPagerIndicator_spi_dotSelectedRadius, 0);
        mDotNormalRadius = dotNormalSize;

        if (dotMediumSize >= mDotNormalRadius) {
            dotMediumSize = getDefaultRadiusForType(DotType.MEDIUM);
        }
        mDotMediumRadius = dotMediumSize;

        if (dotSmallSize >= mDotMediumRadius) {
            dotSmallSize = getDefaultRadiusForType(DotType.SMALL);
        }
        mDotSmallRadius = dotSmallSize;

        if (dotSelectedSize < mDotNormalRadius) {
            dotSelectedSize = getDefaultRadiusForType(DotType.SELECTED);
        }
        mDotSelectedRadius = dotSelectedSize;

        mSpacing = (int) (8 * context.getResources().getDisplayMetrics().density);
        spaceBetweenDotCenters = (int) (attributes.getDimensionPixelSize(R.styleable.ScrollingPagerIndicator_spi_dotSpacing, 0) + mDotNormalRadius);
        minVisibleDotCount = attributes.getInt(R.styleable.ScrollingPagerIndicator_spi_minVisibleDotCount, 2);
        attributes.recycle();

        paint = new Paint();
        paint.setAntiAlias(true);

        if (isInEditMode()) {
            setDotCount(7);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(preMeasureWidth, preMeasureHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mDotHolders.size(); ++i) {
            DotHolder dotHolder = mDotHolders.get(i);
            if (dotHolder.type == DotType.SELECTED) {
                paint.setColor(mSelectedDotColor);
            } else {
                paint.setColor(mDotColor);
            }
            canvas.drawCircle(dotHolder.centerX, dotHolder.centerY, dotHolder.radius, paint);
        }
    }

    /**
     * @return not selected dot color
     */
    @ColorInt
    public int getDotColor() {
        return mDotColor;
    }

    /**
     * Sets dot color
     *
     * @param color dot color
     */
    public void setDotColor(@ColorInt int color) {
        mDotColor = color;
        invalidate();
    }

    /**
     * @return the selected dot color
     */
    @ColorInt
    public int getSelectedDotColor() {
        return mSelectedDotColor;
    }

    /**
     * Sets selected dot color
     *
     * @param color selected dot color
     */
    public void setSelectedDotColor(@ColorInt int color) {
        mSelectedDotColor = color;
        invalidate();
    }

    /**
     * Sets dot count
     *
     * @param count new dot count
     */
    public void setDotCount(int count) {
        initDots(count);
    }

    /**
     * Attaches indicator to ViewPager
     *
     * @param pager pager to attach
     */
    public void attachToPager(@NonNull ViewPager pager) {
        attachToPager(pager, new ViewPagerAttacher());
    }

    /**
     * Attaches to any custom pager
     *
     * @param pager    pager to attach
     * @param attacher helper which should setup this indicator to work with custom pager
     */
    public <T> void attachToPager(@NonNull final T pager, @NonNull final PagerAttacher<T> attacher) {
        detachFromPager();
        attacher.attachToPager(this, pager);
        currentAttacher = attacher;

        attachRunnable = () -> {
            mItemCount = -1;
            attachToPager(pager, attacher);
        };
    }

    /**
     * Detaches indicator from pager.
     */
    public void detachFromPager() {
        if (currentAttacher != null) {
            currentAttacher.detachFromPager();
            currentAttacher = null;
            attachRunnable = null;
        }
        dotCountInitialized = false;
    }

    /**
     * Detaches indicator from pager and attaches it again.
     * It may be useful for refreshing after adapter count change.
     */
    public void reattach() {
        if (attachRunnable != null) {
            attachRunnable.run();
            invalidate();
        }
    }

    /**
     * This method must be called from ViewPager.OnPageChangeListener.onPageScrolled or from some
     * similar callback if you use custom PagerAttacher.
     *
     * @param page   index of the first page currently being displayed
     */
    public void onPageSettled(int page) {
        if (page < 0 || page != 0 && page >= mItemCount) {
            throw new IndexOutOfBoundsException("page must be [0, adapter.getItemCount())");
        }
        if (mPageIndex == page)
            return;

        boolean isSwipeToLeft = page > mPageIndex;
        boolean shouldTranslate = false;
        mPageIndex = page;
        int newDotIndex = isSwipeToLeft ? mSelectedDotIndex + 1 : mSelectedDotIndex - 1;

        // If number of item >= maxDotNumber, we have a dot type NO at either end of list
        // Depends on swipe direction, we will move that dot to the end(swipe left) or to the start(swipe right) of list
        //  so that we'll have a beautiful animation
        // E.g:   | S M N N N N SEL M S NO | and swipe right --> we transform into | NO S M N N N N SEL M S | so we can make
        // animation for this
        if (isSwipeToLeft) {
            DotHolder holder = mDotHolders.get(0);
            if (holder.type == DotType.NONE
                    && 1 < mDotHolders.size() && mDotHolders.get(1).type == DotType.SMALL
                    && mItemCount - NUMBER_OF_SMALL_SIZE_DOTS > mPageIndex) {
                --newDotIndex;
                --mSelectedDotIndex;

                mDotHolders.remove(0);
                DotHolder lastHolder = mDotHolders.get(mDotHolders.size() - 1);
                holder.centerX = lastHolder.centerX + spaceBetweenDotCenters;
                mDotHolders.add(holder);
            }
        } else {
            DotHolder holder = mDotHolders.get(mDotHolders.size() - 1);
            if (holder.type == DotType.NONE && mDotHolders.size() > 1
                    && mDotHolders.get(mDotHolders.size() - 2).type == DotType.SMALL
                    && mPageIndex >= mSelectedDotIndex) {
                ++newDotIndex;
                ++mSelectedDotIndex;

                mDotHolders.remove(mDotHolders.size() - 1);
                DotHolder firstHolder = mDotHolders.get(0);
                holder.centerX = firstHolder.centerX - spaceBetweenDotCenters;
                mDotHolders.add(0, holder);
            }
        }

        // Determine if should start translate animation or not
        if (isSwipeToLeft && newDotIndex > NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS - 1) {
            int nextIndex = mSelectedDotIndex + 1;
            if (nextIndex < mDotHolders.size()) {
                DotHolder nextHolder = mDotHolders.get(nextIndex);
                if (nextHolder.type == DotType.MEDIUM) {
                    shouldTranslate = true;
                }
            }
        } else if (!isSwipeToLeft) {
            int prevIndex = mSelectedDotIndex - 1;
            if (prevIndex >= 0) {
                DotHolder prevHolder = mDotHolders.get(prevIndex);
                if (prevHolder.type == DotType.MEDIUM) {
                    shouldTranslate = true;
                }
            }
        }
        mSelectedDotIndex = newDotIndex;

        // Then, translate the dots or just simply update the selected one
        if (shouldTranslate) {
            if (isSwipeToLeft) {
                boolean hasShiftLeftNormal = false;
                for (int i = 0; i < mDotHolders.size(); ++i) {
                    DotType newType = DotType.NORMAL;

                    DotHolder holder = mDotHolders.get(i);
                    DotHolder prevHolder = null;
                    int prevIndex = i - 1;
                    if (prevIndex >= 0) {
                        prevHolder = mDotHolders.get(prevIndex);
                    }

                    DotType currType = holder.type;

                    switch (currType) {
                        case SELECTED:
                            newType = DotType.NORMAL;
                            break;
                        case NORMAL:
                            if (!hasShiftLeftNormal) {
                                newType = DotType.MEDIUM;
                                hasShiftLeftNormal = true;
                            } else {
                                newType = DotType.NORMAL;
                            }
                            break;
                        case MEDIUM:
                            // Go to tiny or normal or selected
                            if (prevHolder != null) {
                                if (prevHolder.type == DotType.NORMAL) {
                                    newType = DotType.NORMAL;
                                    if (i == mSelectedDotIndex) {
                                        newType = DotType.SELECTED;
                                    }
                                } else {
                                    newType = DotType.SMALL;
                                }
                            } else {
                                newType = DotType.SMALL;
                            }
                            break;
                        case SMALL:
                            // Go to medium or none
                            if (prevHolder != null) {
                                if (prevHolder.type == DotType.NORMAL || prevHolder.type == DotType.SELECTED) {
                                    newType = DotType.MEDIUM;
                                } else {
                                    newType = DotType.NONE;
                                }
                            } else {
                                newType = DotType.NONE;
                            }
                            break;
                        case NONE:
                            if (mPageIndex < mItemCount - NUMBER_OF_SMALL_SIZE_DOTS) {
                                if (prevHolder != null && prevHolder.type == DotType.MEDIUM) {
                                    newType = DotType.SMALL;
                                } else {
                                    newType = DotType.NONE;
                                }
                            } else {
                                newType = DotType.NONE;
                            }
                            break;
                    }

                    updateDotType(holder, newType, true);
                }
            } else {
                for (int i = mDotHolders.size() - 1; i >= 0; --i) {
                    DotType newType = DotType.NORMAL;

                    DotHolder holder = mDotHolders.get(i);
                    DotHolder nextHolder = null;

                    int nextIndex = i + 1;
                    if (nextIndex < mDotHolders.size()) {
                        nextHolder = mDotHolders.get(nextIndex);
                    }

                    DotType currType = holder.type;

                    switch (currType) {
                        case SELECTED:
                            newType = DotType.NORMAL;
                            break;
                        case NORMAL:
                            if (nextHolder != null) {
                                if (nextHolder.type == DotType.SMALL || nextHolder.type == DotType.NONE) {
                                    newType = DotType.MEDIUM;
                                } else if (nextHolder.type == DotType.NORMAL || nextHolder.type == DotType.MEDIUM) {
                                    newType = DotType.NORMAL;
                                } else {
                                    newType = DotType.MEDIUM;
                                }
                            } else { // case only 6 items, and this is the rightmost one
                                newType = DotType.MEDIUM;
                            }
                            break;
                        case MEDIUM:
                            // Go to tiny or normal or selected
                            if (nextHolder != null) {
                                if (nextHolder.type == DotType.NORMAL) {
                                    newType = DotType.NORMAL;
                                    if (i == mSelectedDotIndex) {
                                        newType = DotType.SELECTED;
                                    }
                                } else {
                                    newType = DotType.SMALL;
                                }
                            } else {
                                newType = DotType.SMALL;
                            }
                            break;
                        case SMALL:
                            if (nextHolder != null) {
                                if (nextHolder.type == DotType.NORMAL || nextHolder.type == DotType.SELECTED) {
                                    newType = DotType.MEDIUM;
                                } else {
                                    newType = DotType.NONE;
                                }
                            } else {
                                newType = DotType.NONE;
                            }
                            break;
                        case NONE:
                            if (nextHolder != null && nextHolder.type == DotType.MEDIUM) {
                                newType = DotType.SMALL;
                            } else {
                                newType = DotType.NONE;
                            }
                            break;
                    }

                    updateDotType(holder, newType, false);
                }
            }
        } else {
            for (int i = 0; i < mDotHolders.size(); ++i) {
                DotHolder holder = mDotHolders.get(i);

                if (i == mSelectedDotIndex) {
                    holder.type = DotType.SELECTED;
                    holder.radius = mDotSelectedRadius;
                } else {
                    if (holder.type == DotType.SELECTED) {
                        holder.type = DotType.NORMAL;
                        holder.radius = mDotNormalRadius;
                    }
                }
            }
        }

        invalidate();
    }

    private void initDots (int itemCount) {
        if (mItemCount == itemCount && dotCountInitialized) {
            return;
        }
        if (itemCount < minVisibleDotCount)
            return;

        mSelectedDotIndex = 0;
        mItemCount = itemCount;
        mDotHolders = new ArrayList<>(itemCount);
        dotCountInitialized = true;

        int size = Math.min(itemCount, MAX_NUMBER_OF_DOTS);

        // Use normal size is ok for this
        preMeasureWidth = (int) (size * mDotNormalRadius + (size - 1) * spaceBetweenDotCenters) + 2 * mSpacing;
        preMeasureHeight = (int) (mDotSelectedRadius * 2) + 4; // prevent the rounding issue
        float centerX = preMeasureWidth * 1f / 2;

        if (itemCount > NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS) {
            // It will be sth like |       SEL N N N N M T | at the beginning
            int smallRightAmount = Math.min(NUMBER_OF_SMALL_SIZE_DOTS, itemCount - NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS);
            centerX -= smallRightAmount * spaceBetweenDotCenters;
        } else {
            // | SEL N N N N |
            centerX = preMeasureWidth * 1f / itemCount;
        }

        // We draw a max number of dots, and if item count is larger, we + 1 (this additional will be
        //  the NONE type for smooth transition)
        if (size >= MAX_NUMBER_OF_DOTS)
            size += 1;

        // Create the DotHolder list
        // Initially it will be sth like |       SEL N N N N M T NO | so when we swipe left it will translate like we want
        for (int i = 0; i < size; ++i) {
            DotType type;
            float radius;
            if (i == 0) {
                radius = mDotSelectedRadius;
                type = DotType.SELECTED;
            } else if (i == NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS) {
                radius = mDotMediumRadius;
                type = DotType.MEDIUM;
            } else if (i == NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS + 1) {
                radius = mDotSmallRadius;
                type = DotType.SMALL;
            } else if (i < NUMBER_OF_NORMAL_AND_SELECTED_SIZE_DOTS) {
                radius = mDotNormalRadius;
                type = DotType.NORMAL;
            } else {
                // The rest are invisible, so we can do translate animation later
                radius = 0;
                type = DotType.NONE;
            }
            mDotHolders.add(new DotHolder(centerX, preMeasureHeight * 1f / 2, radius, type));
            centerX += spaceBetweenDotCenters;
        }

        requestLayout();
        invalidate();
    }

    private void updateDotType(DotHolder holder, DotType newType, boolean translateToLeft) {
        holder.type = newType;
        float newRadius = getRadiusForType(holder.type);
        float newX = holder.centerX;
        if (translateToLeft) {
            newX -= spaceBetweenDotCenters;
        } else {
            newX += spaceBetweenDotCenters;
        }
        holder.animate(newX, newRadius);
    }

    private float getRadiusForType(DotType type) {
        switch (type) {
            case SELECTED:
                return mDotSelectedRadius;
            case NORMAL:
                return mDotNormalRadius;
            case MEDIUM:
                return mDotMediumRadius;
            case SMALL:
                return mDotSmallRadius;
            default:
                return 0;
        }
    }

    private float getDefaultRadiusForType(DotType type) {
        switch (type) {
            case SELECTED:
                return mDotNormalRadius / 1.8f;
            case NORMAL:
                return mDotNormalRadius / 2f;
            case MEDIUM:
                return mDotNormalRadius / 4f;
            case SMALL:
                return mDotNormalRadius / 8f;
            default:
                return 0;
        }
    }

    public class DotHolder {
        float centerX;
        float centerY;
        float radius;
        DotType type;
        ValueAnimator valueAnimator;

        public DotHolder(float centerX, float centerY, float radius, DotType type) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.type = type;
        }

        public void animate(float newCenterX, float newRadius) {
            if (valueAnimator != null && valueAnimator.isRunning()) {
                valueAnimator.pause();
            }

            final float diffRadius = newRadius - radius;
            final float startR = radius;
            valueAnimator = ValueAnimator.ofFloat(centerX, newCenterX);
            valueAnimator.setDuration(200);
            valueAnimator.addUpdateListener(animation -> {
                centerX = (float) animation.getAnimatedValue();
                radius = startR + diffRadius * animation.getAnimatedFraction();
                invalidate();
            });
            valueAnimator.start();
        }
    }

    /**
     * Interface for attaching to custom pagers.
     *
     * @param <T> custom pager's class
     */
    public interface PagerAttacher<T> {

        /**
         * Here you should add all needed callbacks to track pager's item count, position and offset
         * You must call:
         * {@link ScrollingPagerIndicator#setDotCount(int)} - initially and after page selection,
         * {@link ScrollingPagerIndicator#onPageSettled(int)} - initially and after page selection,
         * {@link ScrollingPagerIndicator#reattach()} - each time your adapter items change.
         *
         * @param indicator indicator
         * @param pager     pager to attach
         */
        void attachToPager(@NonNull ScrollingPagerIndicator indicator, @NonNull T pager);

        /**
         * Here you should unregister all callbacks previously added to pager and adapter
         */
        void detachFromPager();
    }
}
