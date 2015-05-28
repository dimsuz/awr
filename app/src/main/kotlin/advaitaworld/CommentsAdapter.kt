package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.View
import advaitaworld.parsing.CommentNode
import android.widget.TextView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View.OnClickListener
import android.support.v4.view.ViewCompat
import advaitaworld.parsing.PostData
import advaitaworld.parsing.emptyPostData
import rx.Observable
import rx.android.lifecycle.LifecycleEvent
import rx.Subscription
import rx.android.lifecycle.LifecycleObservable
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import advaitaworld.parsing.User
import timber.log.Timber
import android.widget.ImageView
import advaitaworld.CommentsAdapter.CommentViewHolder
import advaitaworld.util.setVisible
import advaitaworld.CommentsAdapter.PostViewHolder
import com.squareup.picasso.Picasso
import android.net.Uri

/**
 * Adapter that represents a post and its comments
 */
class CommentsAdapter(val lifecycle: Observable<LifecycleEvent>, val showPost: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    companion object {
        val ITEM_TYPE_CONTENT = 0
        val ITEM_TYPE_COMMENT = 1
    }

    private var expandCommentAction: ((CommentNode) -> Unit)? = null
    private var commentClickAction: ((CommentNode) -> Unit)? = null
    private var data: List<ItemInfo> = listOf()
    private var postData: PostData = emptyPostData()
    private var userDataSubscription: Subscription? = null
    private val userInfoMap: MutableMap<String, User> = hashMapOf()

    public fun swapData(postData: PostData, data: List<ItemInfo>) {
        this.postData = postData
        this.data = data
        notifyDataSetChanged()
        startFetchingUserInfo()
    }

    private fun startFetchingUserInfo() {
        if(userDataSubscription != null) {
            userDataSubscription!!.unsubscribe()
        }
        val userNames = if(showPost) arrayListOf(postData.content.author) else arrayListOf()
        val userData = UserInfoProvider.getUsersByName(data.mapTo(userNames) { it.node.content.author })
        userDataSubscription = LifecycleObservable.bindUntilLifecycleEvent(lifecycle, userData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { onUserInfoUpdated(it) },
                        { exception -> Timber.e(exception, "failed to fetch user info") },
                        { Timber.i("user info fetched, info map contains ${userInfoMap.size()} items") })
    }

    // see if data or postData has content by this user => notify update
    private fun onUserInfoUpdated(user: User) {
        // if showing post by this author, include its position right away at the top, adjust others
        val posAdjust = if(showPost) 1 else 0
        val positions = if(postData.content.author == user.name) arrayListOf(0) else arrayListOf()
        data.mapIndexedTo(positions) { i, item -> if(item.node.content.author == user.name) i+posAdjust else -1 }
                .filter { it >= 0 }
        // update info to be used when rebinding view
        if(!positions.isEmpty()) {
            userInfoMap.put(user.name, user)
        }
        //Timber.d("updating positions $positions")
        for(pos in positions) {
            notifyItemChanged(pos)
        }
    }

    fun getData(): List<ItemInfo> {
        return data
    }

    public fun setCommentExpandAction(action: (CommentNode) -> Unit) {
        expandCommentAction = action
    }

    public fun setCommentClickAction(action: (CommentNode) -> Unit) {
        commentClickAction = action
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        return when(viewType) {
            ITEM_TYPE_CONTENT -> {
                PostViewHolder(inflater.inflate(R.layout.post_content, parent, false))
            }
            ITEM_TYPE_COMMENT -> {
                CommentViewHolder(inflater.inflate(R.layout.comment, parent, false), expandCommentAction, commentClickAction)
            }
            else -> throw RuntimeException("unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(getItemViewType(position)) {
            ITEM_TYPE_CONTENT -> bindPostHolder(holder as PostViewHolder, postData, userInfoMap)
            ITEM_TYPE_COMMENT -> {
                val pos = if (showPost) position - 1 else position
                bindCommentHolder(holder as CommentViewHolder, data.get(pos), userInfoMap)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if(showPost && position == 0) ITEM_TYPE_CONTENT else ITEM_TYPE_COMMENT
    }

    override fun getItemCount(): Int {
        return data.size() + if(showPost) 1 else 0
    }

    // required for hasStableIds()
    override fun getItemId(position: Int): Long {
        if(showPost && position == 0) return -2
        val pos = if (showPost) position - 1 else position
        return if(!data.isEmpty()) data.get(pos).node.id() else -1
    }

    /**
     * Returns an item info at the position.
     * Will throw if there's no item at this position or if item is of another type (a post content)
     */
    public fun getItemInfo(position: Int) : ItemInfo {
        val pos = if (showPost) position - 1 else position
        return data.get(pos)
    }

    trait ItemInfoHolder {
        var itemInfo : ItemInfo?
    }

    class CommentViewHolder(itemView: View, expandAction: ((CommentNode) -> Unit)?, clickAction: ((CommentNode) -> Unit)?) :
            RecyclerView.ViewHolder(itemView), OnClickListener, ItemInfoHolder {

        val avatarView = itemView.findViewById(R.id.avatar) as ImageView
        val authorView = itemView.findViewById(R.id.author_name) as TextView
        val dateView = itemView.findViewById(R.id.date) as TextView
        val ratingView = itemView.findViewById(R.id.rating) as TextView
        val textView = itemView.findViewById(R.id.text) as TextView
        val expandText = itemView.findViewById(R.id.expand_comment) as TextView
        // NOTE temporarily using FrameLayout to use selectableItemBackground
        // (when this is no longer the case, just rename expandText to expandView and remove line below)
        val expandView = expandText.getParent() as View
        val expandAction: ((CommentNode) -> Unit)? = expandAction
        val clickAction: ((CommentNode) -> Unit)? = clickAction
        val ratingNegColor = itemView.getResources().getColor(R.color.rating_bg_negative)
        val ratingPosColor = itemView.getResources().getColor(R.color.rating_bg_positive)
        override var itemInfo : ItemInfo? = null

        init {
            if(expandAction != null) {
                expandView.setOnClickListener(this)
            }
            if(clickAction != null) {
                itemView.setOnClickListener(this)
            }
        }

        override fun onClick(view: View) {
            val n = itemInfo?.node
            val action = when(view) {
                expandView -> expandAction
                itemView -> clickAction
                else -> null
            }
            if(n != null && action != null) {
                action(n)
            }
        }
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarView = itemView.findViewById(R.id.avatar) as ImageView
        val titleView = itemView.findViewById(R.id.post_title) as TextView
        val subtitleView = itemView.findViewById(R.id.post_subtitle) as TextView
        val contentView = itemView.findViewById(R.id.post_content) as TextView
    }
}

private fun bindPostHolder(holder: PostViewHolder, data: PostData, userInfoMap: Map<String, User>) {
    updateAvatarView(holder.avatarView, data.content.author, userInfoMap)
    holder.titleView.setText(data.title)
    holder.subtitleView.setText(data.content.author)
    holder.contentView.setText(data.content.text)

    setContentElevation(holder, isTopContent = true)
}

private fun bindCommentHolder(holder: CommentViewHolder, itemInfo: ItemInfo, userInfoMap: Map<String, User>) {
    val content = itemInfo.node.content
    updateAvatarView(holder.avatarView, content.author, userInfoMap)
    holder.authorView.setText(content.author)
    holder.dateView.setText(content.dateString)
    if(content.rating != null) {
        holder.ratingView.setText(content.rating)
        holder.ratingView.setBackgroundColor(if(content.rating.startsWith('-')) holder.ratingNegColor else holder.ratingPosColor)
    }
    holder.ratingView.setVisible(content.rating != null)
    holder.textView.setText(content.text)
    if(!itemInfo.isInStaircase && itemInfo.node.deepChildCount != 0 && holder.getAdapterPosition() != 0) {
        val count = itemInfo.node.deepChildCount
        val s = holder.expandText.getResources().getQuantityString(R.plurals.commentses, count, count)
        holder.expandView.setVisible(true)
        holder.expandText.setText(s)
    } else {
        holder.expandView.setVisible(false)
    }
    holder.itemInfo = itemInfo

    // 'staircase' of replies can appear only on top and all views in it must share the same
    // higher elevation - as they go first
    setContentElevation(holder, isTopContent = itemInfo.isInStaircase || holder.getAdapterPosition() == 0)
}

private fun updateAvatarView(avatarView: ImageView, userName: String, userInfoMap: Map<String, User>) {
    val user = userInfoMap.get(userName)
    if(user != null) {
        Picasso.with(avatarView.getContext())
                .load(Uri.parse(user.avatarUrl))
                .placeholder(R.drawable.placeholder_avatar)
                .error(if(BuildConfig.DEBUG) R.drawable.placeholder_error else R.drawable.placeholder_avatar)
                .into(avatarView)
    } else {
        avatarView.setImageResource(R.drawable.placeholder_avatar)
    }
}

private fun setContentElevation(holder: RecyclerView.ViewHolder, isTopContent: Boolean) {
    // FIXME think of something to emulate this nicely on pre 5.0
    if(isTopContent) {
        ViewCompat.setElevation(holder.itemView, holder.itemView.getResources().getDimension(R.dimen.elevation_high))
    } else {
        ViewCompat.setElevation(holder.itemView, holder.itemView.getResources().getDimension(R.dimen.elevation_low))
    }
}

