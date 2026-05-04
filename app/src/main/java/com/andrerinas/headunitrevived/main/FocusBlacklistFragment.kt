package com.andrerinas.headunitrevived.main

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.utils.Settings
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*

class FocusBlacklistFragment : Fragment() {

    private lateinit var settings: Settings
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var loadingText: TextView
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppEntry> = emptyList()
    private var blacklist: MutableSet<String> = mutableSetOf()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class AppEntry(
        val name: String,
        val packageName: String,
        val icon: Drawable?
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_focus_blacklist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = App.provide(requireContext()).settings
        blacklist = settings.focusStealBlacklist.toMutableSet()

        recyclerView = view.findViewById(R.id.recycler_view)
        searchBar = view.findViewById(R.id.search_bar)
        loadingText = view.findViewById(R.id.loading_text)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { navigateBack() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })

        adapter = AppListAdapter(
            onToggle = { packageName, isChecked ->
                if (isChecked) {
                    blacklist.add(packageName)
                } else {
                    blacklist.remove(packageName)
                }
                // Save immediately
                settings.focusStealBlacklist = blacklist
            },
            isBlacklisted = { packageName -> packageName in blacklist }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadApps()
    }

    private fun loadApps() {
        loadingText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = requireContext().packageManager
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                // Filter to only launchable apps (has a launcher intent) and exclude ourselves
                val myPackage = requireContext().packageName
                installedApps
                    .filter { appInfo ->
                        appInfo.packageName != myPackage &&
                        pm.getLaunchIntentForPackage(appInfo.packageName) != null
                    }
                    .map { appInfo ->
                        AppEntry(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = appInfo.packageName,
                            icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                        )
                    }
                    .sortedWith(compareByDescending<AppEntry> { it.packageName in blacklist }
                        .thenBy { it.name.lowercase() })
            }

            if (isAdded) {
                allApps = apps
                adapter.submitList(apps)
                loadingText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun filterApps(query: String) {
        if (query.isBlank()) {
            adapter.submitList(allApps)
        } else {
            val filtered = allApps.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }

    private fun navigateBack() {
        try {
            if (!findNavController().navigateUp()) {
                requireActivity().finish()
            }
        } catch (e: Exception) {
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }

    // --- RecyclerView Adapter ---

    class AppListAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val isBlacklisted: (String) -> Boolean
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        private var items: List<AppEntry> = emptyList()

        fun submitList(newItems: List<AppEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_focus_blacklist, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.appName.text = entry.name
            holder.appPackage.text = entry.packageName
            entry.icon?.let { holder.appIcon.setImageDrawable(it) }

            // Prevent triggering the listener during programmatic update
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = isBlacklisted(entry.packageName)

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(entry.packageName, isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.checkbox.toggle()
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.app_icon)
            val appName: TextView = view.findViewById(R.id.app_name)
            val appPackage: TextView = view.findViewById(R.id.app_package)
            val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
        }
    }
}
