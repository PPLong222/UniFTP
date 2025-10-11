package com.github.pplong.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import kotlin.random.Random

class TestViewModel(helloWorld: String, val dao: TestDao): ViewModel() {
    private val _timer = MutableStateFlow(0)
    val timer = _timer.asStateFlow()

    private val _testEntities = MutableStateFlow<List<TestEntity>>(emptyList())
    val testEntities = _testEntities.asStateFlow()

    init {
        startTimer()
        println(helloWorld)
    }

    fun startTimer() {
        viewModelScope.launch {
            while(true) {
                delay(1000)
                _timer.value++
            }
        }
    }

    fun insertTest() {
        val i = Random.nextInt()
        val entity = TestEntity(0, i.toString())
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(entity)
        }
    }

    fun getTestEntities() {
        viewModelScope.launch {
            dao.getAllAsFlow().collect {
                _testEntities.value = it
            }
        }
    }
}