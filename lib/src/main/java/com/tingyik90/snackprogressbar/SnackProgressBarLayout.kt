package com.tingyik90.snackprogressbar

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.snackbar.ContentViewCallback
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_img_icon
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_layout_background
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_layout_main
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_layout_roundedBackground
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_progressbar_circular_determinate
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_progressbar_circular_indeterminate
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_progressbar_horizontal
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_txt_action
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_txt_actionNextLine
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_txt_message
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_txt_progress
import kotlinx.android.synthetic.main.snackprogressbar.view.snackProgressBar_txt_progress_circular
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Layout class for SnackProgressBar.
 */
@Keep
class SnackProgressBarLayout : LinearLayout, ContentViewCallback {

    /* constructors */
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @TargetApi(21)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    /* companion object */
    @Keep
    companion object {

        internal const val ACTION_DOWN = 123
        internal const val SWIPE_OUT = 456
        internal const val SWIPE_IN = 789

        // Animation duration as per BaseTransientBottomBar
        internal const val ANIMATION_DURATION = 250L

    }

    /* views */
    val backgroundLayout: SnackProgressBarLayout by lazy { snackProgressBar_layout_background }
    val roundedBackgroundLayout: LinearLayout by lazy { snackProgressBar_layout_roundedBackground }
    val mainLayout: LinearLayout by lazy { snackProgressBar_layout_main }
    val iconImage: ImageView by lazy { snackProgressBar_img_icon }
    val messageText: TextView by lazy { snackProgressBar_txt_message }
    val actionText: TextView by lazy { snackProgressBar_txt_action }
    val actionNextLineText: TextView by lazy { snackProgressBar_txt_actionNextLine }
    val progressText: TextView by lazy { snackProgressBar_txt_progress }
    val progressTextCircular: TextView by lazy { snackProgressBar_txt_progress_circular }
    val horizontalProgressBar: ProgressBar by lazy { snackProgressBar_progressbar_horizontal }
    val circularDeterminateProgressBar: ProgressBar by lazy { snackProgressBar_progressbar_circular_determinate }
    val circularIndeterminateProgressBar: ProgressBar by lazy { snackProgressBar_progressbar_circular_indeterminate }

    /* parameters */
    // As per Behavior in BaseTransientBottomBar
    private val startAlphaSwipeDistance = 0.1f
    private val endAlphaSwipeDistance = 0.6f
    private val swipeOutVelocity = 800f

    // Height as per Material Design
    private val heightSingle = resources.getDimension(R.dimen.snackProgressBar_height_single).toInt()
    private val heightMulti = resources.getDimension(R.dimen.snackProgressBar_height_multi).toInt()
    private val heightActionNextLine = resources.getDimension(R.dimen.snackProgressBar_height_actionNextLine).toInt()

    // Use fixed dp for comparison purpose
    private val defaultTextSizeDp = resources.getDimension(R.dimen.snackProgressBar_text_body_dp).toInt()
    private var swipeToDismiss: Boolean = false
    private var viewsToMove: Array<WeakReference<View>>? = null
    private var onBarTouchListener: OnBarTouchListener? = null

    /**
     * Interface definition for a callback to be invoked when the SnackProgressBar is touched.
     */
    interface OnBarTouchListener {
        /**
         * Called when the SnackProgressBar is touched.
         *
         * @param event Type of touch event.
         */
        fun onTouch(event: Int)
    }

    /**
     * Registers a callback to be invoked when the SnackProgressBar is touched.
     *
     * @param onBarTouchListener The callback that will run. This value may be null.
     */
    internal fun setOnBarTouchListener(onBarTouchListener: OnBarTouchListener?) {
        this.onBarTouchListener = onBarTouchListener
        setOnTouchListener()
    }

    /**
     * Passes the view (e.g. FloatingActionButton) to move up or down as SnackProgressBar is shown or dismissed.
     *
     * @param viewsToMove Views to animate when the SnackProgressBar is shown or dismissed.
     */
    internal fun setViewsToMove(viewsToMove: Array<View>?) {
        this.viewsToMove = viewsToMove?.map { WeakReference(it) }?.toTypedArray()
    }

    /**
     * Sets whether to use rounded corner background for SnackProgressBar according to new Material Design.
     * Note that the background cannot be changed after being shown.
     *
     * @param useRoundedCornerBackground Whether to use rounded corner background for SnackProgressBar.
     */
    fun useRoundedCornerBackground(useRoundedCornerBackground: Boolean) {
        if (useRoundedCornerBackground) {
            roundedBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.snackprogressbar_background_rounded)
            val layoutParams = roundedBackgroundLayout.layoutParams as MarginLayoutParams
            val margin =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            layoutParams.setMargins(margin, 0, margin, margin)
            roundedBackgroundLayout.layoutParams = layoutParams
        } else {
            roundedBackgroundLayout.background = null
        }
    }

    /**
     * Updates the color of the layout.
     *
     * @param backgroundColor  R.color id.
     * @param messageTextColor R.color id.
     * @param actionTextColor  R.color id.
     * @param progressBarColor R.color id.
     * @param progressTextColor R.color id.
     */
    internal fun setColor(
        @ColorRes backgroundColor: Int, @ColorRes messageTextColor: Int,
        @ColorRes actionTextColor: Int, @ColorRes progressBarColor: Int,
        @ColorRes progressTextColor: Int
    ) {
        // Always make sure the parent is transparent, else it will be black around the rounder corner drawable background
        (backgroundLayout.parent as FrameLayout).setBackgroundColor(
            ContextCompat.getColor(
                context,
                android.R.color.transparent
            )
        )
        if (roundedBackgroundLayout.background == null) {
            backgroundLayout.setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
        } else {
            backgroundLayout.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            roundedBackgroundLayout.background.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(context, backgroundColor),
                PorterDuff.Mode.SRC_IN
            )
        }
        messageText.setTextColor(ContextCompat.getColor(context, messageTextColor))
        actionText.setTextColor(ContextCompat.getColor(context, actionTextColor))
        actionNextLineText.setTextColor(ContextCompat.getColor(context, actionTextColor))
        horizontalProgressBar.progressDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, progressBarColor),
            PorterDuff.Mode.SRC_IN
        )
        circularDeterminateProgressBar.progressDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, progressBarColor),
            PorterDuff.Mode.SRC_IN
        )
        horizontalProgressBar.indeterminateDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, progressBarColor),
            PorterDuff.Mode.SRC_IN
        )
        circularIndeterminateProgressBar.indeterminateDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, progressBarColor),
            PorterDuff.Mode.SRC_IN
        )
        progressText.setTextColor(ContextCompat.getColor(context, progressTextColor))
        progressTextCircular.setTextColor(ContextCompat.getColor(context, progressTextColor))
    }

    /**
     * Sets the text size of SnackProgressBar.
     *
     * @param px Font size in pixels.
     */
    internal fun setTextSize(px: Float) {
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        actionText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        actionNextLineText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        progressText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        // Not changed for progressTextCircular as it will be out of the progressBar.
    }

    /**
     * Sets the max lines for message.
     *
     * @param maxLines Number of lines.
     */
    internal fun setMaxLines(maxLines: Int) {
        messageText.maxLines = maxLines
    }

    /**
     * Sets whether user can swipe to dismiss.
     *
     * @param swipeToDismiss Whether user can swipe to dismiss.
     */
    internal fun setSwipeToDismiss(swipeToDismiss: Boolean) {
        this.swipeToDismiss = swipeToDismiss
    }

    /**
     * onMeasure for determining actionNextLine and message height
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val lineCount = messageText.lineCount
        val textSize = messageText.textSize.toInt()
        val hasAction = actionText.text.toString().isNotEmpty()
        if (hasAction) {
            // Put the action into next line if width is more than 25% of total width, or if other element is taking the space
            val isActionNextLine =
                (actionText.measuredWidth.toFloat() / backgroundLayout.measuredWidth.toFloat() > 0.25f)
                        || circularDeterminateProgressBar.visibility == View.VISIBLE
                        || circularIndeterminateProgressBar.visibility == View.VISIBLE
                        || progressText.visibility == View.VISIBLE
                        || progressTextCircular.visibility == View.VISIBLE
            if (isActionNextLine) {
                // Set actionNextLineLayout height
                val height = if (textSize <= defaultTextSizeDp) {
                    heightActionNextLine
                } else {
                    heightActionNextLine + (textSize - defaultTextSizeDp)
                }
                val layoutParams = actionNextLineText.layoutParams
                if (layoutParams.height != height) {
                    layoutParams.height = height
                    actionNextLineText.layoutParams = layoutParams
                }
                actionText.visibility = View.GONE
                actionNextLineText.visibility = View.VISIBLE
            } else {
                actionText.visibility = View.VISIBLE
                actionNextLineText.visibility = View.GONE
            }
        } else {
            actionText.visibility = View.GONE
            actionNextLineText.visibility = View.GONE
        }
        // Set layout height according to message length
        val height: Int = when (lineCount) {
            1 -> {
                if (textSize <= defaultTextSizeDp) {
                    heightSingle
                } else {
                    heightSingle + (textSize - defaultTextSizeDp)
                }
            }
            2 -> {
                if (textSize <= defaultTextSizeDp) {
                    heightMulti
                } else {
                    heightMulti + 2 * (textSize - defaultTextSizeDp)
                }
            }
            else -> (heightMulti + (lineCount * textSize - 2 * defaultTextSizeDp))
        }
        val layoutParams = messageText.layoutParams
        if (layoutParams.height != height) {
            layoutParams.height = height
            messageText.layoutParams = layoutParams
            // Remeasure after height change
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * onAttachedToWindow
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Clear the padding of the parent that hold this view
        val parentView = parent as View
        parentView.setPadding(0, 0, 0, 0)
    }

    /**
     * Animation for when updateTo() is called by SnackProgressBarManager and size changed
     */
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        if (oldH != 0 && oldH != h) {
            viewsToMove?.forEach {
                val viewToMove = it.get()
                if (viewToMove != null) {
                    ViewCompat.animate(viewToMove).translationYBy((oldH - h).toFloat()).setDuration(ANIMATION_DURATION)
                        .start()
                }
            }
        }
    }

    /**
     * Animation as per original Snackbar class
     */
    override fun animateContentIn(delay: Int, duration: Int) {
        val viewsToAnimate = arrayOf(
            iconImage,
            messageText,
            actionText,
            actionNextLineText,
            progressText,
            horizontalProgressBar,
            circularDeterminateProgressBar,
            circularIndeterminateProgressBar
        )
        val parent = parent as? View
        val bottomMargin = if (parent != null) {
            val layoutParams = parent.layoutParams as MarginLayoutParams
            layoutParams.bottomMargin
        } else {
            0
        }
        viewsToAnimate.forEach { viewToAnimate ->
            if (viewToAnimate.visibility == View.VISIBLE) {
                viewToAnimate.alpha = 0f
                ViewCompat.animate(viewToAnimate).alpha(1f).setDuration(duration.toLong())
                    .setStartDelay(delay.toLong()).start()
            }
        }
        viewsToMove?.forEach {
            val viewToMove = it.get()
            if (viewToMove != null) {
                ViewCompat.animate(viewToMove).translationY((-1 * (measuredHeight + bottomMargin)).toFloat())
                    .setInterpolator(FastOutSlowInInterpolator())
                    .setDuration(ANIMATION_DURATION).start()
            }
        }
    }

    /**
     * Animation as per original Snackbar class
     */
    override fun animateContentOut(delay: Int, duration: Int) {
        val viewsToAnimate = arrayOf(
            iconImage,
            messageText,
            actionText,
            actionNextLineText,
            progressText,
            horizontalProgressBar,
            circularDeterminateProgressBar,
            circularIndeterminateProgressBar
        )
        viewsToAnimate.forEach { viewToAnimate ->
            if (viewToAnimate.visibility == View.VISIBLE) {
                viewToAnimate.alpha = 1f
                ViewCompat.animate(viewToAnimate).alpha(0f).setDuration(duration.toLong())
                    .setStartDelay(delay.toLong()).start()
            }
        }
        viewsToMove?.forEach {
            val viewToMove = it.get()
            if (viewToMove != null) {
                ViewCompat.animate(viewToMove).translationY(0f)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .setDuration(ANIMATION_DURATION).start()
            }
        }
    }

    /**
     * Sets onTouchListener to allow swipe to dismiss behaviour for layouts other than CoordinatorLayout.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setOnTouchListener() {
        backgroundLayout.setOnTouchListener(object : OnTouchListener {

            private var startX: Float = 0f
            private var endX: Float = 0f
            private var velocityTracker: VelocityTracker? = null

            @SuppressLint("Recycle")
            private fun getVelocityTracker(): VelocityTracker {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain()
                }
                return velocityTracker!!
            }

            private fun recycleVelocityTracker() {
                velocityTracker?.recycle()
                velocityTracker = null
            }

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (!swipeToDismiss) return true
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Callback onBarTouchListener
                        onBarTouchListener?.onTouch(ACTION_DOWN)
                        // Track initial coordinate
                        startX = event.rawX
                        // Track velocity
                        getVelocityTracker().addMovement(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Track velocity
                        getVelocityTracker().addMovement(event)
                        // Track move coordinate
                        val moveX = event.rawX
                        // Set translationX
                        val deltaX = moveX - startX
                        val parentView = view.parent as View
                        parentView.translationX = deltaX
                        // Animate alpha as per behaviour specified in BaseTransientBottomBar for CoordinatorLayout
                        val totalWidth = parentView.measuredWidth
                        val fractionTravelled = abs(deltaX / totalWidth)
                        when {
                            fractionTravelled < startAlphaSwipeDistance -> parentView.alpha = 1f
                            fractionTravelled > endAlphaSwipeDistance -> parentView.alpha = 0f
                            else -> parentView.alpha = 1f - (fractionTravelled - startAlphaSwipeDistance) /
                                    (endAlphaSwipeDistance - startAlphaSwipeDistance)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Track final coordinate
                        endX = event.rawX
                        // Get velocity and return resources
                        val velocityTracker = getVelocityTracker()
                        velocityTracker.computeCurrentVelocity(1000)
                        val velocity = abs(velocityTracker.getXVelocity(pointerId))
                        recycleVelocityTracker()
                        // Animate layout
                        var toSwipeOut = false
                        val parentView = view.parent as View
                        // Swipe out if layout moved more than half of the screen
                        if (abs(endX - startX) / parentView.width > 0.5) {
                            toSwipeOut = true
                        }
                        // Swipe out if velocity is high
                        if (abs(velocity) > swipeOutVelocity) {
                            toSwipeOut = true
                        }
                        if (toSwipeOut) {
                            swipeOut(endX - startX)
                        } else {
                            // Else, return to original position
                            swipeIn(endX - startX)
                        }
                        // To satisfy android accessibility
                        view.performClick()
                    }
                }
                return true
            }
        })
    }

    /**
     * Swipe out animation.
     *
     * @param deltaX Difference between position of ACTION_DOWN and ACTION_UP.
     * Positive value means user swiped to right.
     */
    private fun swipeOut(deltaX: Float) {
        // Callback onBarTouchListener
        onBarTouchListener?.onTouch(SWIPE_OUT)
        val parentView = parent as View
        val direction = if (deltaX > 0f) 1f else -1f
        ViewCompat.animate(parentView)
            .translationX(direction * parentView.width)
            .setInterpolator(FastOutSlowInInterpolator())
            .setDuration(ANIMATION_DURATION)
            .setListener(null)      // Remove listener that is attached in animateViewIn() of BaseTransientBottomBar
            .start()
        ViewCompat.animate(parentView)
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .start()
    }

    /**
     * Animation for swipe in.
     *
     * @param deltaX Difference between position of ACTION_DOWN and ACTION_UP. Zero means a user click.
     */
    private fun swipeIn(deltaX: Float) {
        val parentView = parent as View
        // Animate if the layout has moved
        if (Math.abs(deltaX) >= 0f) {
            ViewCompat.animate(parentView)
                .translationX(0f)
                .setInterpolator(FastOutSlowInInterpolator())
                .setDuration(ANIMATION_DURATION)
                .setListener(null)      // Remove listener that is attached in animateViewIn() of BaseTransientBottomBar
                .start()
            ViewCompat.animate(parentView)
                .alpha(1f)
                .setDuration(ANIMATION_DURATION)
                .start()
        } else {
            // Else, just make sure the layout is at correct position
            parentView.translationX = 0f
            parentView.alpha = 1f
        }
        // Callback onBarTouchListener
        onBarTouchListener?.onTouch(SWIPE_IN)
    }

}