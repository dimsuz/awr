package advaitaworld

import advaitaworld.PostFeedAdapter.ViewHolder
import advaitaworld.parsing.ProfileInfo
import advaitaworld.parsing.ShortPostInfo
import advaitaworld.parsing.User
import advaitaworld.util.*
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

public class PostFeedAdapter(resources: Resources,
                             private val voteRequestSender: VoteRequestSender,
                             private val lifecycle: Observable<LifecycleEvent>) : RecyclerView.Adapter<ViewHolder>() {
    private var data: List<ShortPostInfo> = listOf()
    private var currentUser: ProfileInfo? = null
    private val userInfoMap: MutableMap<String, User> = hashMapOf()
    private var userDataSubscription: Subscription? = null
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

    public fun swapData(data: List<ShortPostInfo>, currentUser: ProfileInfo?) {
        this.data = data
        this.currentUser = currentUser
        notifyDataSetChanged()
        startFetchingUserInfo()
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
        holder.comments.setText(post.commentCount ?: "")
        holder.expandButton.setVisible(post.isExpandable)
        holder.bindVoteViews(currentUser?.name, post.content.author, post.content.userVote)
        holder.bindRatingView(post.content.rating ?: "")
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
        val ratingView = itemView.findViewById(R.id.rating) as TextView
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
            setupClickListeners()
        }

        private fun setupClickListeners() {
            val listener = { isVoteUp : Boolean ->
                animateVoteStart(isVoteUp)
                val postId = data.get(getAdapterPosition()).postId
                val observable : Observable<String>
                val successAction : (String) -> Unit
                val errorAction : (Throwable) -> Unit
                when(isVoteUp) {
                    true -> {
                        observable = voteRequestSender.sendVoteRequest(postId, true)
                        successAction = { newRating: String -> animateVoteUpFinish(true, newRating) }
                        // FIXME show error to user somehow
                        errorAction = { Timber.e(it, "failed to update rating"); animateVoteUpFinish(false, newRating = "") }
                    }
                    else -> {
                        observable = voteRequestSender.sendVoteRequest(postId, false)
                        successAction = { newRating: String -> animateVoteDownFinish(true, newRating) }
                        // FIXME show error to user somehow
                        errorAction = { Timber.e(it, "failed to update rating"); animateVoteUpFinish(false, newRating = "") }
                    }
                }
                LifecycleObservable.bindUntilLifecycleEvent(lifecycle, observable, LifecycleEvent.DESTROY)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(successAction, errorAction)
            }

            voteUpButton.setOnClickListener { listener(true) }
            voteDownButton.setOnClickListener { listener(false) }
        }

        private fun animateVoteStart(isVoteUp: Boolean) {
            val context = voteUpButton.getContext()
            val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_quad)
            val dy = - voteUpButton.getHeight().toFloat()
            val upAnim = voteUpButton.animate().translationYBy(dy).alpha(0f)
                .setInterpolator(interpolator)
                .setDuration(200)
            val downAnim = voteDownButton.animate().translationYBy(dy).alpha(0f)
                .setInterpolator(interpolator)
                .setDuration(200)
            if(isVoteUp) { downAnim.setStartDelay(100) } else { upAnim.setStartDelay(100) }

            voteProgress.setVisible(true)
            voteProgress.setAlpha(0f)
            voteProgress.setScaleX(0.2f)
            voteProgress.setScaleY(0.2f)
            voteProgress.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.overshoot))
                .setDuration(200)
                .setStartDelay(300)
        }

        private fun animateVoteUpFinish(success: Boolean, newRating: String) {
            animateVoteFinish(
                selectedVoteButton = voteUpButton,
                otherVoteButton = voteDownButton,
                selectedDrawable = voteUpVotedDrawable,
                newRating = newRating)
        }

        private fun animateVoteDownFinish(success: Boolean, newRating: String) {
            animateVoteFinish(
                selectedVoteButton = voteDownButton,
                otherVoteButton = voteUpButton,
                selectedDrawable = voteDownVotedDrawable,
                newRating = newRating)
        }

        private fun animateVoteFinish(selectedVoteButton: ImageView,
                                      otherVoteButton: ImageView,
                                      selectedDrawable: Drawable,
                                      newRating: String) {

            val context = selectedVoteButton.getContext()
            val overShootInterpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.overshoot)
            val moveVoteResultDown = {
                selectedVoteButton.animate().alpha(1f).setDuration(200)
                selectedVoteButton.animate().translationY(0f).setDuration(200)
                    .setInterpolator(overShootInterpolator)
                Unit
            }
            val showNewRatingAndVoteButton = {
                ratingView.setScaleX(0.2f)
                ratingView.setScaleY(0.2f)
                ratingView.animate().alpha(1f).setDuration(200)
                ratingView.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(overShootInterpolator)
                    .withEndActionCompat(moveVoteResultDown)
            }
            //
            // Animation starts here
            // It is sequenced in a way to prevent layout passes happening during animations
            //
            // - hide progress bar
            // - remove it from layout
            // - also remove other (non-voted) button
            // - set new rating
            // - wait until layout pass finishes
            // - add voted button to layout, with alpha 0
            // - animate vote text view show, this would alter layout
            // - animate selected button show, it would move from above in already altered
            //   by new rating position (shifted to right)
            voteProgress.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndActionCompat {
                    voteProgress.setVisible(false)
                    // other vote button will no longer be needed, reset its state, hide
                    otherVoteButton.setVisible(false)
                    otherVoteButton.setTranslationY(0f)
                    otherVoteButton.setAlpha(1f)
                    // vote up needs its drawable tinted before coming back
                    selectedVoteButton.setImageDrawable(selectedDrawable)
                    // actually needed only for vote_down, see rating_actions.xml
                    if(selectedVoteButton.getMarginLeft() != 0) {
                        selectedVoteButton.adjustMargin(-selectedVoteButton.getMarginLeft(), 0, 0, 0)
                    }
                    bindRatingView(newRating)
                    ratingView.setAlpha(0f)

                    // above changes will request layout pass, wait for it to finish before continuing
                    // with animation
                    ratingView.post(showNewRatingAndVoteButton)
                }
        }

        // sets drawables and resets any results of previous animation on these views,
        fun bindVoteViews(currentUserName: String?, postAuthorName: String, userVote: Int) {
            // FIXME track if request is not in progress and only then reset animation properties
            // otherwise set them up correctly for animation to finish
            clearAnimationEffects()

            // can vote only when logged in and post is not by logged in user
            val canVote = currentUserName != null && currentUserName != postAuthorName

            voteUpButton.setVisible(canVote && userVote >= 0)
            voteUpButton.setImageDrawable(if(userVote > 0) voteUpVotedDrawable else voteUpDrawable)

            voteDownButton.setVisible(canVote && userVote <= 0)
            voteDownButton.setImageDrawable(if(userVote < 0) voteDownVotedDrawable else voteDownDrawable)

            // this needs to correspond with rating_actions.xml
            val leftMargin = voteUpButton.getResources().getDimensionPixelSize(R.dimen.post_action_view_height)
            voteDownButton.setMargins(if(userVote >= 0) leftMargin else 0, 0, 0, 0)
        }

        fun bindRatingView(rating: String) {
            ratingView.setText(rating)
            ratingView.setVisible(rating.isNotEmpty())
            if(rating.isNotEmpty()) {
                val color = if (rating.charAt(0) != '-') R.color.rating_positive else R.color.rating_negative
                ratingView.setTextColor(ratingView.getResources().getColor(color))
            }
        }

        // clears any possible effects left from previously running animation
        private fun clearAnimationEffects() {
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

            ratingView.setAlpha(1f)
        }

    }

}

public interface VoteRequestSender {
    /**
     * Sends a rating vote request and returns a new rating as string
     */
    fun sendVoteRequest(postId: String, isVoteUp: Boolean) : Observable<String>
}