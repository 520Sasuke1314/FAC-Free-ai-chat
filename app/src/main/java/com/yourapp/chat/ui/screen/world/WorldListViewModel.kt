package com.yourapp.chat.ui.screen.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.data.repository.WorldEntryRepository
import com.yourapp.chat.domain.parser.CharacterCardParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorldListUiState(
    /** 世界书集合（一次导入一个文件 = 一个集合） */
    val books: List<WorldBookEntity> = emptyList(),
    /** 手动添加的全局条目（不属于任何集合） */
    val manualEntries: List<WorldEntryEntity> = emptyList(),
    /** 各集合下的条目，key = bookId */
    val entriesByBook: Map<Long, List<WorldEntryEntity>> = emptyMap(),
    /** 已展开的集合 id（展开态挂在 VM：条目标被 LazyColumn 虚拟化回收后仍保持展开） */
    val expandedBookIds: Set<Long> = emptySet(),
    /** 编辑中的集合 id（null = 手动条目） */
    val editingBookId: Long? = null,
    val editing: WorldEntryEntity? = null,
    val showEditor: Boolean = false,
    val importMessage: String? = null,
    val importError: Boolean = false
)

class WorldListViewModel(
    private val repository: WorldEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldListUiState())
    val uiState: StateFlow<WorldListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
        viewModelScope.launch {
            repository.getManualGlobal().collect { entries ->
                _uiState.update { it.copy(manualEntries = entries) }
            }
        }
    }

    /** 打开集合详情（加载其条目）；已加载过则跳过，避免重复收集 */
    fun openBook(bookId: Long) {
        if (_uiState.value.entriesByBook.containsKey(bookId)) return
        viewModelScope.launch {
            repository.getEntriesByBook(bookId).collect { entries ->
                _uiState.update { state ->
                    state.copy(entriesByBook = state.entriesByBook + (bookId to entries))
                }
            }
        }
    }

    /** 展开/收起集合：展开时确保其条目已加载 */
    fun toggleBookExpanded(bookId: Long) {
        val wantExpand = !_uiState.value.expandedBookIds.contains(bookId)
        _uiState.update {
            it.copy(expandedBookIds = if (wantExpand) it.expandedBookIds + bookId else it.expandedBookIds - bookId)
        }
        if (wantExpand) openBook(bookId)
    }

    fun openNew() {
        _uiState.update {
            it.copy(
                editingBookId = null,
                editing = WorldEntryEntity(cardId = null, keys = "", content = "", priority = 100),
                showEditor = true
            )
        }
    }

    fun openEdit(entry: WorldEntryEntity) {
        _uiState.update { it.copy(editing = entry, showEditor = true) }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(showEditor = false, editing = null) }
    }

    fun save(keys: String, content: String, priority: Int, comment: String) {
        val current = _uiState.value.editing ?: return
        val updated = current.copy(
            keys = keys.trim(),
            content = content,
            priority = priority,
            enabled = true, // 默认启用，实际由对话中用户选择控制
            comment = comment.ifBlank { null }
        )
        viewModelScope.launch {
            if (updated.id == 0L) {
                repository.insert(updated)
            } else {
                repository.update(updated)
            }
            _uiState.update { it.copy(showEditor = false, editing = null) }
        }
    }

    fun delete(entry: WorldEntryEntity) {
        viewModelScope.launch {
            repository.delete(entry)
        }
    }

    // 移除全局 toggle，改为在对话中由用户手动选择世界书

    fun deleteBook(book: WorldBookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun renameBook(book: WorldBookEntity, newName: String) {
        viewModelScope.launch {
            repository.renameBook(book, newName.trim())
        }
    }

    /** 导入世界书文件（PNG 或 JSON）：一个文件 = 一个集合 */
    fun importFile(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val app = ChatApplication.instance
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法打开文件")
                val bytes = input.use { it.readBytes() }
                val tmp = java.io.File(app.cacheDir, "world_import_${System.currentTimeMillis()}")
                tmp.writeBytes(bytes)
                val entries = CharacterCardParser.parseWorldFile(tmp)
                tmp.delete()

                val displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?.take(30) ?: "世界书"
                val bookId = repository.createBook(displayName.ifBlank { "世界书" })
                entries.forEach { w ->
                    repository.insert(
                        WorldEntryEntity(
                            cardId = null,
                            bookId = bookId,
                            keys = w.keys,
                            content = w.content,
                            enabled = w.enabled,
                            priority = w.priority,
                            comment = w.comment
                        )
                    )
                }
                _uiState.update {
                    it.copy(importMessage = "导入世界书「$displayName」（${entries.size} 条设定）", importError = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importMessage = e.message ?: "导入失败", importError = true) }
            }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return WorldListViewModel(app.worldEntryRepository) as T
            }
        }
    }
}
