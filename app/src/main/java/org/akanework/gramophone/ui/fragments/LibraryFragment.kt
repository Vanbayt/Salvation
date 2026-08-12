package org.akanework.gramophone.ui.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.LibrarySearchViewModel
import androidx.recyclerview.widget.RecyclerView

class LibraryFragment : BaseFragment(true) {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private val searchViewModel: LibrarySearchViewModel by activityViewModels()

    // Ссылки на UI элементы
    private var btnSearch: ImageButton? = null
    // 🔥 Ссылка на кнопку настроек
    private var btnSettings: ImageButton? = null
    private var tvTitle: TextView? = null
    private var etSearch: EditText? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tab_layout_library)
        viewPager = view.findViewById(R.id.view_pager_library)

        btnSearch = view.findViewById(R.id.btn_library_search)
        // 🔥 Используем правильный ID из твоего XML
        btnSettings = view.findViewById(R.id.btn_library_settings)
        tvTitle = view.findViewById(R.id.tv_library_title)
        etSearch = view.findViewById(R.id.et_search_library)

        setupViewPager()
        // 🔥 УМНЫЙ ФИКС СВАЙПОВ (отдаем жест родителю на краях)
        val innerRecyclerView = viewPager.getChildAt(0) as? RecyclerView
        innerRecyclerView?.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            var startX = 0f

            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        // По умолчанию забираем свайп себе
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - startX
                        // dx > 0 это свайп вправо, dx < 0 это свайп влево
                        val direction = if (dx > 0) -1 else 1

                        // Если внутренний список НЕ МОЖЕТ больше скроллиться в эту сторону —
                        // мы отдаем жест главному пейджеру!
                        if (!rv.canScrollHorizontally(direction)) {
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        setupSearchLogic()
        setupBackButton()

        // 🔥 ВЕШАЕМ КЛИК ДЛЯ ОТКРЫТИЯ НАСТРОЕК
        btnSettings?.setOnClickListener {
            (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(SettingsFragment())
        }
    }

    private fun setupSearchLogic() {
        btnSearch?.setOnClickListener {
            if (etSearch?.visibility == View.GONE) {
                openSearch()
            } else {
                closeSearch()
            }
        }

        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchViewModel.updateQuery(s?.toString()?.trim() ?: "")
            }
        })
    }

    private fun openSearch() {
        tvTitle?.visibility = View.GONE
        etSearch?.visibility = View.VISIBLE
        // Прячем кнопку настроек, чтобы дать строке поиска больше места
        btnSettings?.visibility = View.GONE
        etSearch?.requestFocus()
        btnSearch?.setImageResource(R.drawable.ic_close)

        etSearch?.post {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        tvTitle?.visibility = View.VISIBLE
        etSearch?.visibility = View.GONE
        // Возвращаем кнопку настроек
        btnSettings?.visibility = View.VISIBLE
        etSearch?.text?.clear()
        btnSearch?.setImageResource(R.drawable.ic_search)
        etSearch?.clearFocus()

        view?.let { safeView ->
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(safeView.windowToken, 0)
        }
    }

    private fun setupBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (etSearch?.visibility == View.VISIBLE) {
                    closeSearch()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })
    }

    private var tabLayoutMediator: TabLayoutMediator? = null

    private fun setupViewPager() {
        val pagerAdapter = LibraryPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val tabTitles = arrayOf("ТРЕКИ", "АЛЬБОМЫ", "ПЛЕЙЛИСТЫ", "АРТИСТЫ")

        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.apply { attach() }
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        if (::viewPager.isInitialized) {
            viewPager.adapter = null
        }
        btnSearch = null
        btnSettings = null
        tvTitle = null
        etSearch = null
        super.onDestroyView()
    }
}