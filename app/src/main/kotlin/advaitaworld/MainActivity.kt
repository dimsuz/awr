package advaitaworld

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.SpaceItemDecoration
import timber.log.Timber
import rx.schedulers.Schedulers
import advaitaworld.db.Database
import android.view.Menu
import android.view.MenuItem
import android.support.v7.widget.Toolbar
import android.content.Intent
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.support.RxActionBarActivity
import rx.android.lifecycle.LifecycleEvent
import rx.android.schedulers.AndroidSchedulers
import android.support.v4.view.ViewPager
import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.Gravity
import com.advaitaworld.widgets.SlidingTabLayout
import android.content.res.Resources
import android.graphics.Color

public class MainActivity : RxActionBarActivity() {
    var adapter: PostFeedAdapter? = null
    val server: Server by ServerProvider()
    var db: Database? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(getApplicationContext())
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setupTabsAndPager()
        setupPostsList()
        fetchPosts()
    }

    private fun setupTabsAndPager() {
        val viewPager = findViewById(R.id.main_pager) as ViewPager
        viewPager.setAdapter(MainPagesAdapter(getResources()))

        val tabsLayout = findViewById(R.id.tabs) as SlidingTabLayout
        tabsLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent))
        tabsLayout.setTabTitleColors(Color.WHITE, getResources().getColor(R.color.primary_light))
        tabsLayout.setCustomTabView(R.layout.section_tab, R.id.tab_text_view)
        tabsLayout.setViewPager(viewPager)
    }

    private fun setupPostsList() {
        val listView = findViewById(R.id.post_list) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        listView.setHasFixedSize(true)
        listView.addItemDecoration(SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_vertical_margin)))
        adapter = PostFeedAdapter()
        listView.setAdapter(adapter)
    }

    private fun fetchPosts() {
        val postsData = server.getPosts(Section.Popular)
        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), postsData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { postData ->
                            adapter?.swapData(postData)
                            // after saving main data in adapter, start fetching full user info
                            // (avatars, etc)
                            fetchUserInfo(postData.map({ it.content.author }))
                        },
                        { Timber.e(it, "parsing failed with exception") })
    }

    private fun fetchUserInfo(userNames: List<String>) {
        val userData = UserInfoProvider.getUsersByName(userNames)
        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), userData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { adapter?.onUserInfoUpdated(it) },
                        { Timber.e(it, "failed to retrieve a user avatar") })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(Intent(this, javaClass<PostActivity>()))
            return true
        }

        return super.onOptionsItemSelected(item)
    }

}

private class MainPagesAdapter(val resources: Resources) : PagerAdapter() {
    override fun getCount(): Int {
        return Section.values().size()
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = TextView(container.getContext())
        view.setGravity(Gravity.CENTER)
        view.setText(position.toString())
        container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun getPageTitle(position: Int): CharSequence {
        return resources.getString(Section.values()[position].nameResId)
    }
}
