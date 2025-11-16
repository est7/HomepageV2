package com.example.homepagev2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 演示新架构的 Activity
 *
 * 新架构特点：
 * - MainScrollContainer 统一管理滚动
 * - 所有其他 Behavior 都依赖 MainScrollContainer
 * - 消除了所有特殊判断和 ID 检查
 * - 触摸穿透通过 PassThroughFrameLayout 实现
 */
class MainActivityV2 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_v2)

        // 处理系统栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinator)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        setupFaceViewPager()
        setupContentViewPager()
    }

    private fun setupFaceViewPager() {
        val faceViewPager = findViewById<ViewPager2>(R.id.iv_face)

        faceViewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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
                val imageResources = listOf(
                    R.drawable.avatar_gender,
                    R.drawable.avatar_gender,
                    R.drawable.avatar_gender
                )
                imageView.setImageResource(imageResources[position])
            }

            override fun getItemCount(): Int = 3
        }

        faceViewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        faceViewPager.offscreenPageLimit = 1
    }

    private fun setupContentViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)

        val adapter = ContentPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = "Tab ${position + 1}"
        }.attach()
    }
}

/**
 * Content ViewPager 适配器
 */
class ContentPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return ContentFragment.newInstance(position)
    }
}

/**
 * Content Fragment
 */
class ContentFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sub_home_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val adapter = ContentAdapter()

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // 生成测试数据
        val data = List(50) { "Item ${it + 1}" }
        adapter.submitList(data)
    }

    companion object {
        fun newInstance(pageIndex: Int): ContentFragment {
            return ContentFragment().apply {
                arguments = Bundle().apply {
                    putInt("page_index", pageIndex)
                }
            }
        }
    }
}

/**
 * Content Adapter
 */
class ContentAdapter : ListAdapter<String, ContentAdapter.ViewHolder>(DiffCallback()) {

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
