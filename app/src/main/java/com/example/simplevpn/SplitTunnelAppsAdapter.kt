package com.example.simplevpn

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

class SplitTunnelAppsAdapter(
    context: Context,
    private val items: List<SplitTunnelApp>,
    private val selectedPackages: MutableSet<String>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): SplitTunnelApp = items[position]

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_split_tunnel_app, parent, false)
        val item = getItem(position)

        val iconView = view.findViewById<ImageView>(R.id.appIconImageView)
        val labelView = view.findViewById<TextView>(R.id.appLabelTextView)
        val packageView = view.findViewById<TextView>(R.id.appPackageTextView)
        val checkBox = view.findViewById<CheckBox>(R.id.appSelectedCheckBox)

        iconView.setImageDrawable(item.icon)
        labelView.text = item.label
        packageView.text = item.packageName
        checkBox.isChecked = selectedPackages.contains(item.packageName)
        checkBox.isClickable = false
        checkBox.isFocusable = false

        return view
    }
}
