package com.androidrtc.chat.modules.homepage.bean

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 *
 * @author: est8
 * @date: 2024/5/30
 */
class UserInformationViewModel : ViewModel() {

    private val _pagerData = MutableLiveData<List<List<String>>>()
    val pagerData: LiveData<List<List<String>>> = _pagerData

    fun getHomePageInfo() {
        viewModelScope.launch {
            // Simulate data loading
            val data = List(4) { pageIndex ->
                List(20) { itemIndex -> "Item $itemIndex in Page ${pageIndex + 1}" }
            }
            _pagerData.value = data
        }
    }
}