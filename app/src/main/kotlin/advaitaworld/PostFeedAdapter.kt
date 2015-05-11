package advaitaworld

import advaitaworld.PostFeedAdapter.ViewHolder
import advaitaworld.parsing.ShortPostInfo
import advaitaworld.parsing.User
import advaitaworld.util.setVisible
import advaitaworld.util.withEndActionCompat
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import rx.Observable
import rx.Subscription
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber

public class PostFeedAdapter(val resources: Resources, val lifecycle: Observable<LifecycleEvent>) : RecyclerView.Adapter<ViewHolder>() {
    private var data: List<ShortPostInfo> = listOf()
    private val userInfoMap: MutableMap<String, User> = hashMapOf()
    private var userDataSubscription: Subscription? = null
    private var voteAction: ((String, Boolean) -> Unit)? = null
    private val voteUpDrawable : Drawable
    private val voteUpVotedDrawable : Drawable
    private val voteDownDrawable : Drawable
    private val voteDownVotedDrawable : Drawable

    init {
        voteUpDrawable = resources.getDrawable(R.drawable.vote_up)
        voteUpVotedDrawable = DrawableCompat.wrap(resources.getDrawable(R.drawable.vote_up).mutate())
        DrawableCompat.setTint(voteUpVotedDrawable, resources.getColor(R.color.rating_positive))
        voteDownDrawable = resources.getDrawable(R.drawable.vote_down)
        voteDownVotedDrawable = DrawableCompat.wrap(resources.getDrawable(R.drawable.vote_down).mutate())
        DrawableCompat.setTint(voteDownVotedDrawable, resources.getColor(R.color.rating_negative))
    }

    public fun swapData(data: List<ShortPostInfo>) {
        this.data = data
        notifyDataSetChanged()
        startFetchingUserInfo()
    }


    public fun setVoteChangeAction(action: (postId: String, isVoteUp: Boolean) -> Unit) {
        voteAction = action
    }

    private fun startFetchingUserInfo() {
        if(userDataSubscription != null) {
            userDataSubscription!!.unsubscribe()
        }
        val userData = UserInfoProvider.getUsersByName(data.map { it.content.author })
        userDataSubscription = LifecycleObservable.bindUntilLifecycleEvent(lifecycle, userData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { onUserInfoUpdated(it) },
                        { exception -> Timber.e(exception, "failed to fetch user info") },
                        { Timber.i("user info fetched, info map contains ${userInfoMap.size()} items") })
    }

    public fun onUserInfoUpdated(user: User) {
        // see if data has posts by this user => notify update
        val positions = data
                .mapIndexed { i, post -> if(post.content.author == user.name) i else -1 }
                .filter { it >= 0 }
        // update info to be used when rebinding view
        if(!positions.isEmpty()) {
            userInfoMap.put(user.name, user)
        }
        for(pos in positions) {
            notifyItemChanged(pos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        val view = inflater.inflate(R.layout.post_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = data.get(position)
        val userInfo = userInfoMap.get(post.content.author)
        if(userInfo != null) {
            Picasso.with(holder.avatar.getContext())
                    .load(Uri.parse(userInfo.avatarUrl))
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(if(BuildConfig.DEBUG) R.drawable.placeholder_error else R.drawable.placeholder_avatar)
                    .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.placeholder_avatar)
        }
        holder.title.setText(post.title)
        holder.content.setText(post.content.text)
        holder.author.setText(post.content.author)
        holder.timestamp.setText(post.content.dateString)
        holder.rating.setText(post.content.rating ?: "")
        holder.rating.setVisibility(if(post.content.rating != null) View.VISIBLE else View.GONE)
        holder.comments.setText(post.commentCount ?: "")
        holder.expandButton.setVisible(post.isExpandable)
        holder.bindVoteViews(post.content.userVote)
    }

    override fun getItemCount(): Int {
        return data.size()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title = itemView.findViewById(R.id.title) as TextView
        val content = itemView.findViewById(R.id.content) as TextView
        val contentLayout = itemView.findViewById(R.id.content_layout)
        val author = itemView.findViewById(R.id.author_name) as TextView
        val timestamp = itemView.findViewById(R.id.timestamp) as TextView
        val comments = itemView.findViewById(R.id.comments) as TextView
        val rating = itemView.findViewById(R.id.rating) as TextView
        val avatar = itemView.findViewById(R.id.avatar) as ImageView
        val expandButton = itemView.findViewById(R.id.expand_post)
        val voteUpButton = itemView.findViewById(R.id.vote_up) as ImageView
        val voteDownButton = itemView.findViewById(R.id.vote_down) as ImageView
        val voteProgress = itemView.findViewById(R.id.vote_progress_bar)

        init {
            val openPostAction = { view: View ->
                val context = comments.getContext()
                val intent = Intent(context, javaClass<PostActivity>())
                intent.putExtra(EXTRA_POST_ID, data.get(getAdapterPosition()).postId)
                context.startActivity(intent)
            }
            comments.setOnClickListener(openPostAction)
            contentLayout.setOnClickListener(openPostAction)
            if(voteAction != null) {
                voteUpButton.setOnClickListener {
                    animateVote(true)
                    voteAction!!(data.get(getAdapterPosition()).postId, true)
                }
                voteDownButton.setOnClickListener {
                    animateVote(false)
                    voteAction!!(data.get(getAdapterPosition()).postId, false)
                }
            }
        }

        private fun animateVote(isVoteUp: Boolean) {
            val context = voteUpButton.getContext()
            if(isVoteUp) {
                val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_quad)
                val dy = - voteUpButton.getHeight().toFloat()
                voteUpButton.animate().translationYBy(dy).alpha(0f)
                    .setInterpolator(interpolator)
                    .setDuration(300)
                voteDownButton.animate().translationYBy(dy).alpha(0f)
                    .setInterpolator(interpolator)
                    .setDuration(300)
                    .setStartDelay(100)
                voteProgress.setVisible(true)
                voteProgress.setAlpha(0f)
                voteProgress.setScaleX(0.2f)
                voteProgress.setScaleY(0.2f)
                voteProgress.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.overshoot))
                    .setDuration(200)
                    .setStartDelay(200)
                voteUpButton.postDelayed({ animateVoteUpFinish(true) }, 2000)
            }
        }

        private fun animateVoteUpFinish(success: Boolean) {
            val context = voteUpButton.getContext()
            voteProgress.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndActionCompat {
                    voteProgress.setVisible(false)
                    // vote down will no longer be needed, reset its state
                    voteDownButton.setVisible(false)
                    voteDownButton.setTranslationY(0f)
                    voteDownButton.setAlpha(1f)
                    // vote up needs its drawable tinted before coming back
                    voteUpButton.setImageDrawable(voteUpVotedDrawable)
                    rating.setVisible(true)
                    rating.setText("+283")
                    rating.setAlpha(0f)
                    // above changes will request layout pass, wait for it to finish before continuing
                    // with animation
                    rating.post {
                        rating.setScaleX(0.2f)
                        rating.setScaleY(0.2f)
                        rating.animate().alpha(1f).setDuration(200)
                        rating.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.overshoot))
                            .withEndActionCompat {
                                voteUpButton.animate().alpha(1f).setDuration(200)
                                voteUpButton.animate().translationY(0f).setDuration(200)
                                    .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.overshoot))
                            }
                    }
                }
        }

        private fun animateVoteDownFinish(success: Boolean) {

        }

        // sets drawables and resets any results of previous animation on these views,
        fun bindVoteViews(userVote: Int) {
            // FIXME track if request is not in progress and only then reset animation properties
            // otherwise set them up correctly for animation to finish
            voteProgress.setVisible(false)
            voteUpButton.setAlpha(1f)
            voteUpButton.setScaleX(1f)
            voteUpButton.setScaleY(1f)
            voteUpButton.setTranslationX(0f)
            voteUpButton.setTranslationY(0f)


            voteDownButton.setAlpha(1f)
            voteDownButton.setScaleX(1f)
            voteDownButton.setScaleY(1f)
            voteDownButton.setTranslationX(0f)
            voteDownButton.setTranslationY(0f)

            voteUpButton.setVisible(userVote >= 0)
            voteUpButton.setImageDrawable(if(userVote > 0) voteUpVotedDrawable else voteUpDrawable)

            voteDownButton.setVisible(userVote <= 0)
            voteDownButton.setImageDrawable(if(userVote < 0) voteDownVotedDrawable else voteDownDrawable)
        }

    }

}