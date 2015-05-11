package advaitaworld.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator

public fun View.adjustPadding(dLeft: Int, dTop: Int, dRight: Int, dBottom: Int) {
    this.setPadding(this.getPaddingLeft() + dLeft, this.getPaddingTop() + dTop,
            this.getPaddingRight() + dRight, this.getPaddingBottom() + dBottom)
}

public fun View.adjustMargin(dLeft: Int, dTop: Int, dRight: Int, dBottom: Int) {
    // not checking for null, expecting layout parameters to be set!
    val lparams = this.getLayoutParams() as ViewGroup.MarginLayoutParams
    lparams.leftMargin += dLeft
    lparams.rightMargin += dRight
    lparams.topMargin += dTop
    lparams.bottomMargin += dBottom
    this.setLayoutParams(lparams)
}

public fun View.setMargins(left: Int, top: Int, right: Int, bottom: Int) {
    // not checking for null, expecting layout parameters to be set!
    val lparams = this.getLayoutParams() as ViewGroup.MarginLayoutParams
    lparams.setMargins(left, top, right, bottom)
    this.setLayoutParams(lparams)
}

public fun View.getMarginLeft() : Int {
    val lp = this.getLayoutParams()
    if(lp == null) return 0
    val lparams = lp as ViewGroup.MarginLayoutParams
    return lparams.leftMargin
}

public fun View.setVisible(visible: Boolean) {
    this.setVisibility(if(visible) View.VISIBLE else View.GONE)
}

public fun Resources.dpToPx(dp: Int) : Int {
    return Math.round(this.getDisplayMetrics().density * dp)
}

public fun ViewGroup.children() : Sequence<View> {
    val count = this.getChildCount()
    return sequenceOf(0..(count-1)).map { this.getChildAt(it) }
}

public fun ViewPropertyAnimator.withEndActionCompat(action: () -> Unit) {
    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
        this.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                action()
            }
        })
    } else {
        this.withEndAction(action)
    }
}