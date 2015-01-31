package advaitaworld.util

import android.support.v7.widget.RecyclerView
import android.graphics.Rect
import android.view.View

public class SpaceItemDecoration(val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = space
        outRect.right = space
        outRect.bottom = space
        outRect.top = if(parent.getChildPosition(view) == 0) space else 0
    }
}
