package advaitaworld.util

import advaitaworld.CommentsAdapter
import advaitaworld.ItemInfo
import advaitaworld.R
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v4.view.ViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View

public class SpaceItemDecoration(val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = space
        outRect.right = space
        outRect.bottom = space
        outRect.top = if(parent.getChildPosition(view) == 0) space else 0
    }
}

public class DividerItemDecoration(context: Context, orientation: Int) : RecyclerView.ItemDecoration() {
    companion object {
        public val HORIZONTAL_LIST: Int = LinearLayoutManager.HORIZONTAL
        public val VERTICAL_LIST: Int = LinearLayoutManager.VERTICAL
    }

    private val ATTRS = intArray(android.R.attr.listDivider)
    private var mOrientation: Int = HORIZONTAL_LIST
    private var startItem: Int = 0
    private val mDivider: Drawable

    init {
        val a = context.obtainStyledAttributes(ATTRS)
        mDivider = a.getDrawable(0)
        a.recycle()
        setOrientation(orientation)
    }

    public fun setOrientation(orientation: Int) {
        if (orientation != HORIZONTAL_LIST && orientation != VERTICAL_LIST) {
            throw IllegalArgumentException("invalid orientation")
        }
        mOrientation = orientation
    }

    /**
     * Sets the first item position which will get divider at the bottom. All the positions
     * after this one will also get divider. Defaults to 0.
     */
    public fun setStartItem(position: Int) {
        startItem = position
    }

    override fun onDraw(c: Canvas, parent: RecyclerView) {
        if (mOrientation == VERTICAL_LIST) {
            drawVertical(c, parent)
        } else {
            drawHorizontal(c, parent)
        }
    }

    public fun drawVertical(c: Canvas, parent: RecyclerView) {
        val left = parent.getPaddingLeft()
        val right = parent.getWidth() - parent.getPaddingRight()
        val childCount = parent.getChildCount()
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            if(parent.getChildPosition(child) < startItem) {
                continue
            }
            val params = child.getLayoutParams() as RecyclerView.LayoutParams
            val top = child.getBottom() + params.bottomMargin + Math.round(ViewCompat.getTranslationY(child))
            val bottom = top + mDivider.getIntrinsicHeight()
            mDivider.setBounds(left, top, right, bottom)
            mDivider.draw(c)
        }
    }

    public fun drawHorizontal(c: Canvas, parent: RecyclerView) {
        val top = parent.getPaddingTop()
        val bottom = parent.getHeight() - parent.getPaddingBottom()
        val childCount = parent.getChildCount()
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            if(parent.getChildPosition(child) < startItem) {
                continue
            }
            val params = child.getLayoutParams() as RecyclerView.LayoutParams
            val left = child.getRight() + params.rightMargin + Math.round(ViewCompat.getTranslationX(child))
            val right = left + mDivider.getIntrinsicHeight()
            mDivider.setBounds(left, top, right, bottom)
            mDivider.draw(c)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if(parent.getChildPosition(view) < startItem) {
            return super.getItemOffsets(outRect, view, parent, state)
        }
        if (mOrientation == VERTICAL_LIST) {
            outRect.set(0, 0, 0, mDivider.getIntrinsicHeight())
        } else {
            outRect.set(0, 0, mDivider.getIntrinsicWidth(), 0)
        }
    }
}

/**
 * Decorates comment views with indent and margin
 */
public class CommentItemDecoration : RecyclerView.ItemDecoration() {
    var margin : Int = 0

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if(margin == 0) { margin = view.getResources().getDimensionPixelSize(R.dimen.margin) }
        val info = getItemInfo(view, parent)
        if(info == null) { return }
        val holder = parent.getChildViewHolder(view)
        val hoffset = info.indentLevel * margin * 2
        val voffsetTop = if(!info.isInStaircase && holder.getPosition() != 0) margin else 0
        val voffsetBottom = if(holder.getPosition() == state.getItemCount() - 1) margin else 0
        outRect.set(hoffset, voffsetTop, 0, voffsetBottom)
    }
}

private fun getItemInfo(view: View, parent: RecyclerView) : ItemInfo? {
    val holder = parent.getChildViewHolder(view)
    val childType = holder.getItemViewType()
    if (childType == CommentsAdapter.ITEM_TYPE_COMMENT) {
        val infoHolder = holder as CommentsAdapter.ItemInfoHolder
        return infoHolder.itemInfo
    } else {
        return null
    }
}

/**
 * Decorates items in so called 'staircase' - a flow of 1-to-1 discussion between two users
 */
public class StaircaseItemDecoration(resources: Resources) : RecyclerView.ItemDecoration() {
    private val DIVIDER_HEIGHT = resources.dpToPx(1)
    private val DIVIDER_PADDING = resources.dpToPx(64).toFloat()
    private val paint = Paint()

    init {
        paint.setStrokeWidth(DIVIDER_HEIGHT.toFloat())
        paint.setColor(resources.getColor(R.color.lightGray))
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if(parent.getItemAnimator() != null && parent.getItemAnimator().isRunning()) {
            // FIXME after animation ends fade in nicely
            return
        }
        val childCount = parent.getChildCount()
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            if (needsDivider(child, parent)) {
                val y = child.getBottom().toFloat()
                canvas.drawLine(DIVIDER_PADDING, y, canvas.getWidth() - DIVIDER_PADDING, y, paint)
            }
        }
    }

    private fun needsDivider(view: View, parent: RecyclerView): Boolean {
        val itemInfo = getItemInfo(view, parent)
        if (itemInfo == null || !itemInfo.isInStaircase) {
            return false
        }
        // do not decorate last item in the staircase
        // (which has either 0 or > 1 children)
        if (itemInfo.node.children.size() != 1) {
            return false
        }
        return true
    }
}

/**
 * Decorates a child comments with tiny 'threads' to show to which parent they belong to
 */
public class CommentThreadsDecoration(resources: Resources) : RecyclerView.ItemDecoration() {
    private val THREAD_WIDTH = resources.dpToPx(1)
    private val paint = Paint()

    init {
        paint.setStrokeWidth(THREAD_WIDTH.toFloat())
        paint.setColor(resources.getColor(R.color.lightGray))
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if(parent.getItemAnimator() != null && parent.getItemAnimator().isRunning()) {
            // FIXME after animation ends fade in nicely
            return
        }
        val childCount = parent.getChildCount()
        val offset = 16f
        var prevChildBottom = 0f
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            val itemInfo = getItemInfo(child, parent)
            val needsDecor= itemInfo != null && !itemInfo.isInStaircase && itemInfo.indentLevel > 0
            if (needsDecor) {
                val lastItem = parent.getChildPosition(child) == state.getItemCount() - 1
                val left = child.getLeft().toFloat()
                val ytip = child.getTop() + child.getHeight() / 2f
                val ybot = if(!lastItem) child.getBottom().toFloat() else ytip
                canvas.drawLine(left - offset, prevChildBottom, left - offset, ybot, paint) // vertical
                canvas.drawLine(left - offset, ytip, left, ytip, paint) // horizontal
            }
            prevChildBottom = child.getBottom().toFloat()
        }
    }
}
