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