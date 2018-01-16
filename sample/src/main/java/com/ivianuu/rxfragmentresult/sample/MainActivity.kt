package com.ivianuu.rxfragmentresult.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.ivianuu.rxfragmentresult.ResultListener
import com.ivianuu.rxfragmentresult.RxFragmentResult
import com.ivianuu.rxfragmentresult.Transactor
import io.reactivex.disposables.Disposable

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FirstFragment())
                .commitNowAllowingStateLoss()
    }
}

class FirstFragment : Fragment() {

    private var disposable: Disposable? = null

    private val rxFragmentResult by lazy(LazyThreadSafetyMode.NONE) {
        return@lazy RxFragmentResult(
                activity!!.supportFragmentManager,
                object : Transactor {
                    override fun transact(fm: FragmentManager, fragment: Fragment) {
                        fm.beginTransaction()
                                .add(R.id.fragment_container, fragment)
                                .addToBackStack(null)
                                .commitAllowingStateLoss()
                    }
                }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.request).setOnClickListener {
            rxFragmentResult.startCustom<String>(SecondFragment())
                    .subscribe { Log.d("RxFragmentResult", "on result $it ") }
                    .let { disposable = it }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }
}

class SecondFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.send_result).setOnClickListener {
            val text = view.findViewById<EditText>(R.id.input).text.toString()
            (targetFragment as ResultListener?)?.onFragmentResult(targetRequestCode, text)
            activity?.onBackPressed()
        }
    }
}
