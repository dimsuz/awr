package advaitaworld

import advaitaworld.db.Database
import advaitaworld.support.RxActionBarActivity
import advaitaworld.util.LoadIndicator
import advaitaworld.util.SpaceItemDecoration
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.*
import com.advaitaworld.widgets.SlidingTabLayout
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.SectionDrawerItem
import rx.Observable
import rx.Subscription
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.EnumMap
import kotlin.properties.Delegates

public class MainActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()
    private var db: Database? = null
    private var mainPager : ViewPager by Delegates.notNull()
    private val fetchSubscriptions : EnumMap<Section, Subscription> = EnumMap(javaClass<Section>())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(getApplicationContext())
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setupDrawer()
        setupTabsAndPager()
    }

    private fun setupDrawer() {
        Drawer().withActivity(this)
                .withToolbar(findViewById(R.id.toolbar) as Toolbar)
                .addDrawerItems(
                        PrimaryDrawerItem()
                                .withIcon(R.drawable.ic_account_box_grey600_24dp)
                                .withName(R.string.login)
                                .withCheckable(false),
                        PrimaryDrawerItem()
                                .withIcon(this, R.drawable.ic_settings_grey600_24dp)
                                .withName(R.string.settings),
                        SectionDrawerItem().withCapsName(this, R.string.blog_section_traditions),
                        PrimaryDrawerItem().withName("Традиция 1"),
                        PrimaryDrawerItem().withName("Традиция 2"),
                        PrimaryDrawerItem().withName("Традиция 3"),
                        SectionDrawerItem().withCapsName(this, R.string.blog_section_satsang),
                        PrimaryDrawerItem().withName("Сатсанг 1"),
                        PrimaryDrawerItem().withName("Сатсанг 2"),
                        PrimaryDrawerItem().withName("Сатсанг 3"),
                        SectionDrawerItem().withCapsName(this, R.string.blog_section_misc),
                        PrimaryDrawerItem().withName("Misc1 1"),
                        PrimaryDrawerItem().withName("Misc1 2"),
                        PrimaryDrawerItem().withName("Misc1 3")
                )
                .withSelectedItem(-1)
                .build()
    }

    private fun setupTabsAndPager() {
        mainPager = findViewById(R.id.main_pager) as ViewPager
        mainPager.setAdapter(MainPagesAdapter(getResources(), lifecycle()))

        val pageListener = createPageChangeListener(mainPager)

        val tabsLayout = findViewById(R.id.tabs) as SlidingTabLayout
        tabsLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent))
        tabsLayout.setTabTitleColors(Color.WHITE, getResources().getColor(R.color.primary_light))
        tabsLayout.setCustomTabView(R.layout.section_tab, R.id.tab_text_view)
        tabsLayout.setViewPager(mainPager)
        tabsLayout.setOnPageChangeListener(pageListener)
        // ah, the joy... infamous 'no initial page change event' problem...
        mainPager.post { pageListener.onPageSelected(0) }
    }

    private fun createPageChangeListener(viewPager: ViewPager) : ViewPager.OnPageChangeListener {
        return object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                val viewPagerAdapter = viewPager.getAdapter() as MainPagesAdapter
                val section = Section.values()[position]
                val pageListView = viewPagerAdapter.getPageView(section)
                fetchPosts(pageListView, section)
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        }
    }

    private fun fetchPosts(pageListView: RecyclerView, section: Section) {
        if(fetchSubscriptions.get(section) != null && !fetchSubscriptions.get(section).isUnsubscribed()) {
            Timber.d("fetching posts for $section is already in progress, not starting new fetch")
            return
        }
        // FIXME if fetching is completed, do not start it
        val postsData = server.getPosts(section)
        val adapter = PostFeedAdapter(lifecycle())
        val subscription = LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), postsData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(LoadIndicator
                        .createFor(postsData)
                        .withErrorText(R.string.error_msg_data_fetch_failed)
                        .withRetryAction(R.string.action_retry, { fetchPosts(pageListView, section) })
                        .showIn(pageListView, adapter))
                .subscribe(
                        { postData ->
                            adapter.swapData(postData)
                            // after saving main data in adapter, start fetching full user info
                            //fetchUserInfo(postData.map({ it.content.author }))
                        },
                        { Timber.e(it, "parsing failed with exception") })
        fetchSubscriptions.put(section, subscription)
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
        if (id == R.id.action_refresh) {
            val mainAdapter = mainPager.getAdapter() as MainPagesAdapter
            val section = Section.values()[mainPager.getCurrentItem()]
            val listView = mainAdapter.getPageView(section)
            fetchPosts(listView, section)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

}

private class MainPagesAdapter(val resources: Resources, val activityLifecycle: Observable<LifecycleEvent>) : PagerAdapter() {
    val pageViews : EnumMap<Section, RecyclerView> = EnumMap(javaClass<Section>())

    override fun getCount(): Int {
        return Section.values().size()
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = createFeedListView(container.getContext())
        pageViews.put(Section.values()[position], view)
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
        return listView
    }

    public fun getPageView(section: Section): RecyclerView {
        return pageViews.get(section)!!
    }
}

public fun SectionDrawerItem.withCapsName(context: Context, nameRes: Int) : SectionDrawerItem {
    return this.withName(context.getResources().getString(nameRes).toUpperCase())
}

/**
 * Default implementation of Material Drawer is unable to color icon according to the selected
 * text color, do this manually
 */
public fun PrimaryDrawerItem.withIcon(context: Context, iconRes: Int) : PrimaryDrawerItem {
    val icon = ResourcesCompat.getDrawable(context.getResources(), iconRes, context.getTheme())
    val selectedIcon = ResourcesCompat.getDrawable(context.getResources(), iconRes, context.getTheme()).mutate()
    selectedIcon.setColorFilter(context.getResources().getColor(R.color.material_drawer_selected_text), PorterDuff.Mode.SRC_IN)
    return this.withIcon(icon).withSelectedIcon(selectedIcon)
}
