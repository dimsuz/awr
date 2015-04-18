package advaitaworld

import android.app.Activity
import android.content.Context
import android.graphics.PorterDuff
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.Toolbar
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.SectionDrawerItem

public fun createMainNavigationDrawer(activity: Activity) {
    val drawer = Drawer().withActivity(activity)
        .withToolbar(activity.findViewById(R.id.toolbar) as Toolbar)
        .addDrawerItems(
            PrimaryDrawerItem()
                .withIcon(R.drawable.ic_account_box_grey600_24dp)
                .withName(R.string.login)
                .withCheckable(false),
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
            PrimaryDrawerItem().withName("Misc1 3")
        )
        .withSelectedItem(-1)
        .build()
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
