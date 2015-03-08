package advaitaworld.util

import android.view.View
import android.content.res.Resources

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