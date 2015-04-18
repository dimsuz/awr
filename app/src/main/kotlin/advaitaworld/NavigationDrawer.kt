package advaitaworld

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.Toolbar
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.SectionDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.Tagable
import timber.log.Timber


public fun createMainNavigationDrawer(activity: Activity) {
    val items = createDrawerItems(activity)
    val currentActivityIndex = getSelectedIndex(items, activity)
    val drawer = Drawer().withActivity(activity)
        .withToolbar(activity.findViewById(R.id.toolbar) as Toolbar)
        .addDrawerItems(*items)
        .withSelectedItem(currentActivityIndex)
        .build()

    drawer.setOnDrawerItemClickListener { adapterView, view, position, id, drawerItem ->
        val itemActivityClass = drawerItem.getTag() as Class<*>
        if(itemActivityClass != null) {
            val context = adapterView.getContext()
            val flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val intent = Intent(context, itemActivityClass)
            intent.setFlags(flags)
            context.startActivity(intent)
            // drawer moves selection to the clicked item, need to undo that, so it stays on current activity
            drawer.setSelection(currentActivityIndex, false)
        } else {
            Timber.d("got null tag for pos $position")
        }
    }
}

/**
 * Given a list of items returns an index of item representing the passed Activity or -1 if not found
 */
private fun getSelectedIndex(items: Array<IDrawerItem>, activity: Activity): Int {
    return items.indexOfFirst { it -> it.getTag() == activity.javaClass }
}

private fun createDrawerItems(activity: Activity) : Array<IDrawerItem> {
    return array(
        PrimaryDrawerItem()
            .withTag(javaClass<AuthActivity>())
            .withIcon(activity, R.drawable.ic_account_box_grey600_24dp)
            .withName(R.string.login),
        PrimaryDrawerItem()
            .withTag(javaClass<MainActivity>())
            .withIcon(activity, R.drawable.ic_account_box_grey600_24dp)
            .withName(R.string.home),
        PrimaryDrawerItem()
            .withIcon(activity, R.drawable.ic_settings_grey600_24dp)
            .withName(R.string.settings),
        SectionDrawerItem().withCapsName(activity, R.string.blog_section_traditions),
        PrimaryDrawerItem().withName("Традиция 1"),
        PrimaryDrawerItem().withName("Традиция 2"),
        PrimaryDrawerItem().withName("Традиция 3"),
        SectionDrawerItem().withCapsName(activity, R.string.blog_section_satsang),
        PrimaryDrawerItem().withName("Сатсанг 1"),
        PrimaryDrawerItem().withName("Сатсанг 2"),
        PrimaryDrawerItem().withName("Сатсанг 3"),
        SectionDrawerItem().withCapsName(activity, R.string.blog_section_misc),
        PrimaryDrawerItem().withName("Misc1 1"),
        PrimaryDrawerItem().withName("Misc1 2"),
        PrimaryDrawerItem().withName("Misc1 3"))
}

private fun SectionDrawerItem.withCapsName(context: Context, nameRes: Int) : SectionDrawerItem {
    return this.withName(context.getResources().getString(nameRes).toUpperCase())
}

/**
 * Default implementation of Material Drawer is unable to color icon according to the selected
 * text color, do this manually
 */
private fun PrimaryDrawerItem.withIcon(context: Context, iconRes: Int) : PrimaryDrawerItem {
    val icon = ResourcesCompat.getDrawable(context.getResources(), iconRes, context.getTheme())
    val selectedIcon = ResourcesCompat.getDrawable(context.getResources(), iconRes, context.getTheme()).mutate()
    selectedIcon.setColorFilter(context.getResources().getColor(R.color.material_drawer_selected_text), PorterDuff.Mode.SRC_IN)
    return this.withIcon(icon).withSelectedIcon(selectedIcon)
}
