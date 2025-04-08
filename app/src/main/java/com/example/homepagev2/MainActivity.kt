package com.example.homepagev2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.androidrtc.chat.modules.homepage.bean.UserInformationViewModel
import com.example.homepagev2.databinding.ActivityMainBinding
import com.example.homepagev2.widget.NavItem
import com.google.android.material.tabs.TabLayoutMediator


/**
 * 个人主页
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val pagerAdapter: ViewPagerAdapter by lazy(LazyThreadSafetyMode.NONE) {
        ViewPagerAdapter(this)
    }
    val viewModel: UserInformationViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 为根视图设置左右和顶部的 padding（如果需要）
            binding.root.setPadding(
                systemBars.left,
                systemBars.top,  // 处理状态栏
                systemBars.right,
                0  // 底部由导航栏单独处理
            )

            // 为导航栏设置底部 padding
            binding.navigationBar.setPadding(
                binding.navigationBar.paddingLeft,
                binding.navigationBar.paddingTop,
                binding.navigationBar.paddingRight,
                systemBars.bottom
            )

            // 返回 insets 以便其他视图也能处理
            insets
        }


        parseIntent(intent)
        initView()
        observeData()
        initData()
    }

    private fun initData() {
        viewModel.getHomePageInfo()

    }

    private fun parseIntent(intent: Intent) {
//        activityId = intent.getIntExtra("id", 0)
    }


    fun initView() {
        setupStatusBar()
        setupNavigationBar()
        setupToolbar()
        setupViewPager()
        setupTabLayout()
    }

    private fun setupNavigationBar() {
        val navigationBar = binding.navigationBar
        // 创建导航项
        val navItems = listOf(
            NavItem(
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                ContextCompat.getDrawable(this, R.drawable.avatar_gender),
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                "首页",
                false,
                0
            ),
            NavItem(
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                ContextCompat.getDrawable(this, R.drawable.avatar_gender),
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                "消息",
                false,
                5
            ),
            NavItem(
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                ContextCompat.getDrawable(this, R.drawable.avatar_gender),
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                "联系人",
                false,
                0
            ), NavItem(
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                ContextCompat.getDrawable(this, R.drawable.avatar_gender),
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                "好饿",
                false,
                4
            ),

            NavItem(
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                ContextCompat.getDrawable(this, R.drawable.avatar_gender),
                ContextCompat.getDrawable(this, R.drawable.ic_home),
                "我的",
                false,
                2
            )
        )

        navigationBar.setNavItems(navItems)

        // 可以设置初始选中项
        navigationBar.selectItem(0)


        navigationBar.setOnClickItemListener { index, navItem ->
            //show toast
            Toast.makeText(this, "Clicked item $index", Toast.LENGTH_SHORT).show()
            //根据不同的index，跳转到不同的页面
            when (index) {
                0 -> {
                    //首页
                }

                1 -> {
                    //消息
                }

                2 -> {
                    //联系人
                }

                3 -> {
                    //我的
                }
            }
        }

        navigationBar.setOnDoubleClickItemListener { index, navItem ->
            //show toast
            Toast.makeText(this, "Double Clicked item $index", Toast.LENGTH_SHORT).show()
            //根据不同的index，跳转到不同的页面
            when (index) {
                0 -> {
                    //首页
                }

                1 -> {
                    //消息
                }

                2 -> {
                    //联系人
                }

                3 -> {
                    //我的
                    navigationBar.updateBadgeCount(index, 0)
                }
            }
        }

    }


    private fun setupStatusBar() {
//        immersionBar {
//            statusBarColor(R.color.transparent)
//            navigationBarColor(R.color.transparent)
//        }
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

//        binding.appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
//            val percentage = Math.abs(verticalOffset).toFloat() / appBarLayout.totalScrollRange
//            binding.toolbar.alpha = percentage
//            binding.sivPicture.scaleX = 1 + percentage * 0.2f
//            binding.sivPicture.scaleY = 1 + percentage * 0.2f
//        })
    }

    private fun setupViewPager() {
        binding.vp.adapter = pagerAdapter
        //
        binding.ivFace.apply {
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): RecyclerView.ViewHolder {
                    val imageView = ImageView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    return object : RecyclerView.ViewHolder(imageView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val imageView = holder.itemView as ImageView
                    // Replace R.drawable.image1, R.drawable.image2, etc. with your actual image resources
                    val imageResources = listOf(
                        R.drawable.avatar_gender,
                        R.drawable.avatar_gender,
                        R.drawable.avatar_gender,
                        R.drawable.avatar_gender,
                        R.drawable.avatar_gender,
                        R.drawable.avatar_gender,
                    )
                    imageView.setImageResource(imageResources[position])
                }

                override fun getItemCount(): Int = 5
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
        }

    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.stl, binding.vp) { tab, position ->
            tab.text = "Tab ${position + 1}"
        }.attach()
    }


    private fun observeData() {
        viewModel.pagerData.observe(this, { data ->
            pagerAdapter.updateData(data)
        })
    }


}

class Page1Fragment : PageFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment1_sub_home_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    }

    companion object {
        fun newInstance(pageIndex: Int): Page1Fragment {
            return Page1Fragment().apply {
                arguments = Bundle().apply {
                    putInt("page_index", pageIndex)
                }
            }
        }
    }
}


open class PageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val adapter = PageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sub_home_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    fun updateData(data: List<String>) {
        adapter.submitList(data)
    }

    companion object {
        fun newInstance(pageIndex: Int): PageFragment {
            return PageFragment().apply {
                arguments = Bundle().apply {
                    putInt("page_index", pageIndex)
                }
            }
        }
    }
}

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableListOf<PageFragment>()

    init {
        fragments.add(Page1Fragment.newInstance(0))
        for (i in 1 until 4) {
            fragments.add(PageFragment.newInstance(i))
        }
    }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun updateData(data: List<List<String>>) {
        fragments.forEachIndexed { index, fragment ->
            fragment.updateData(data[index])
        }
    }
}

class PageAdapter : ListAdapter<String, PageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_sub_home_page_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text_view)

        fun bind(item: String) {
            textView.text = item
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}


