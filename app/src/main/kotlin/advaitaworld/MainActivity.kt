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
import com.advaitaworld.widgets.SlidingTabLayout
import android.content.res.Resources
import android.graphics.Color
import android.content.Context
import java.util.EnumMap
import rx.Observable

public class MainActivity : RxActionBarActivity() {
    val server: Server by ServerProvider()
    var db: Database? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(getApplicationContext())
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setupTabsAndPager()
    }

    private fun setupTabsAndPager() {
        val viewPager = findViewById(R.id.main_pager) as ViewPager
        viewPager.setAdapter(MainPagesAdapter(getResources(), lifecycle()))

        val pageListener = createPageChangeListener(viewPager)

        val tabsLayout = findViewById(R.id.tabs) as SlidingTabLayout
        tabsLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent))
        tabsLayout.setTabTitleColors(Color.WHITE, getResources().getColor(R.color.primary_light))
        tabsLayout.setCustomTabView(R.layout.section_tab, R.id.tab_text_view)
        tabsLayout.setViewPager(viewPager)
        tabsLayout.setOnPageChangeListener(pageListener)
        // ah, the joy... infamous 'no initial page change event' problem...
        viewPager.post { pageListener.onPageSelected(0) }
    }

    private fun createPageChangeListener(viewPager: ViewPager) : ViewPager.OnPageChangeListener {
        return object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                val viewPagerAdapter = viewPager.getAdapter() as MainPagesAdapter
                val section = Section.values()[position]
                val adapter = viewPagerAdapter.getPageAdapter(section)
                fetchPosts(adapter, section)
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        }
    }

    private fun fetchPosts(adapter: PostFeedAdapter, section: Section) {
        // FIXME if fetching for this section is already in progress, do nothing
        val postsData = server.getPosts(section)
        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), postsData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { postData ->
                            adapter.swapData(postData)
                            // after saving main data in adapter, start fetching full user info
                            // (avatars, etc)
                            //fetchUserInfo(postData.map({ it.content.author }))
                        },
                        { Timber.e(it, "parsing failed with exception") })
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

private class MainPagesAdapter(val resources: Resources, val activityLifecycle: Observable<LifecycleEvent>) : PagerAdapter() {
    val adapters : EnumMap<Section, PostFeedAdapter> = EnumMap(javaClass<Section>())

    override fun getCount(): Int {
        return Section.values().size()
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = createFeedListView(container.getContext())
        val adapter = view.getAdapter() as PostFeedAdapter
        adapters.put(Section.values()[position], adapter)
        container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun getPageTitle(position: Int): CharSequence {
        return resources.getString(Section.values()[position].nameResId)
    }

    private fun createFeedListView(context: Context) : RecyclerView {
        val listView = RecyclerView(context)
        listView.setLayoutManager(LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false))
        listView.addItemDecoration(SpaceItemDecoration(
                context.getResources().getDimensionPixelSize(R.dimen.card_vertical_margin)))
        listView.setAdapter(PostFeedAdapter(activityLifecycle))
        return listView
    }

    public fun getPageAdapter(section: Section): PostFeedAdapter {
        return adapters.get(section)!!
    }
}
