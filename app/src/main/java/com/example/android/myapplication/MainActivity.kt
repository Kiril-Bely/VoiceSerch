package com.example.android.myapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

import android.nfc.Tag
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Text
import java.util.*
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    private val Tag: String = "MainActivity"

    lateinit var requestInput: TextInputEditText

    lateinit var podsAdapter: SimpleAdapter

    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    val pods = mutableListOf<HashMap<String, String>>()

    lateinit var textToSpeech: TextToSpeech

    var isTtsReady: Boolean = false

    val VOICE_RECOGNITION_COD:Int= 893213

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initWalframIngine()
        initTts()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        requestInput = findViewById<TextInputEditText>(R.id.edit)
        requestInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pods.clear()
                podsAdapter.notifyDataSetChanged()

                val qestion = requestInput.text.toString()
                askWolfram(qestion)

            }
            return@setOnEditorActionListener false
        }


        val podsList: ListView = findViewById(R.id.pods_list)
        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.layout_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.titles, R.id.content)

        )
        podsList.adapter = podsAdapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            if (isTtsReady){
                val title = pods[position]["Title"]
                val contnet = pods[position]["Content"]
                textToSpeech.speak(contnet,TextToSpeech.QUEUE_FLUSH,null,title)
            }
        }
        val voiceInputButton = findViewById<FloatingActionButton>(R.id.voice_button)
        voiceInputButton.setOnClickListener{
            pods.clear()
            podsAdapter.notifyDataSetChanged()

            if(isTtsReady){
                textToSpeech.stop()
            }
            showVoiceInputDialog()
        }

        progressBar = findViewById<ProgressBar>(R.id.progree_bar)

    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.tool_bar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stop -> {
              if (isTtsReady){
                  textToSpeech.stop()
              }
            }
            R.id.action_clear -> {
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun initWalframIngine() {
        waEngine = WAEngine().apply {
            appID = "H4UYVP-KHPX3H4XUT"
            addFormat("plaintext")
        }
    }

    fun showSnackBar(massege: String) {
        Snackbar.make(findViewById(R.id.content), massege, Snackbar.LENGTH_INDEFINITE).apply {
            setAction(android.R.string.ok) {
                dismiss()
            }
            show()
        }
    }

    fun askWolfram(request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            kotlin.runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (result.isError) {
                        showSnackBar((result.errorMessage))
                        return@withContext
                    }

                    if (!result.isSuccess) {
                        requestInput.error = getString(R.string.do_not_understand)
                        return@withContext
                    }

                    for (pod in result.pods) {
                        if (pod.isError) continue
                        val content = StringBuilder()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                        pods.add(0, HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }
                    podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showSnackBar(t.message ?: getString(R.string.somthing_went_wrong))
                }
            }
        }
    }

    fun initTts() {
        textToSpeech = TextToSpeech(this) { code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(Tag, "Tts eror code $code")
                showSnackBar((R.string.tts).toString())
            } else {
                isTtsReady = true
            }
        }
        textToSpeech.language = Locale.US
    }

    fun showVoiceInputDialog()
    {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT,getString(R.string.hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.US)
        }
        kotlin.runCatching {
            startActivityForResult(intent,VOICE_RECOGNITION_COD)

        }.onFailure {t->
            showSnackBar(t.message?:"Voice recognition unavailable")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode== VOICE_RECOGNITION_COD&& resultCode == RESULT_OK){
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let {
                question ->
                requestInput.setText(question)
                askWolfram(question)
            }
        }
    }
}
