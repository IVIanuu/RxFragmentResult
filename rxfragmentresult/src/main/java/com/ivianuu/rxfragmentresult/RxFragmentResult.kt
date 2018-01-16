/*
 * Copyright 2018 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.rxfragmentresult

import android.app.Activity
import android.content.Intent
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import io.reactivex.Maybe
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps activity result calls and returns [Maybe]'s of [FragmentResult]'s
 */
class RxFragmentResult {

    private val fm: FragmentManager
    private val transactor: Transactor

    constructor(activity: FragmentActivity,
                transactor: Transactor) {
        this.fm = activity.supportFragmentManager
        this.transactor = transactor
    }

    constructor(fragment: Fragment,
                transactor: Transactor) {
        this.fm = fragment.childFragmentManager
        this.transactor = transactor
    }

    constructor(fm: FragmentManager,
                transactor: Transactor) {
        this.fm = fm
        this.transactor = transactor
    }

    fun start(requestFragment: Fragment): Maybe<FragmentResult> {
        val resultFragment = RxFragmentResultFragment.get(fm)
        val maybe = resultFragment.registerFragmentResult(requestFragment)
        transactor.transact(fm, requestFragment)
        return maybe
    }

    fun <T> startCustom(requestFragment: Fragment): Maybe<T> {
        val resultFragment = RxFragmentResultFragment.get(fm)
        val maybe = resultFragment.registerFragmentResultCustom<T>(requestFragment)
        transactor.transact(fm, requestFragment)
        return maybe
    }
}

/**
 * Executes fragment transactions
 */
interface Transactor {
    fun transact(fm: FragmentManager, fragment: Fragment)
}

interface ResultListener {
    fun <T> onFragmentResult(requestCode: Int, result: T)
}

/**
 * Represents a fragment result
 */
data class FragmentResult(val requestCode: Int,
                          val resultCode: Int,
                          val data: Intent?) {

    fun isOk() = resultCode == Activity.RESULT_OK

    fun isCanceled() = resultCode == Activity.RESULT_CANCELED
}

/**
 * Handles the activity results
 */
class RxFragmentResultFragment : Fragment(), ResultListener {

    private val subjects = HashMap<Int, PublishSubject<*>>()

    init {
        retainInstance = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!subjects.containsKey(requestCode)) return
        handleActivityResult(requestCode, resultCode, data)
    }

    override fun <T> onFragmentResult(requestCode: Int, result: T) {
        if (!subjects.containsKey(requestCode)) return
        handleFragmentResult(requestCode, result)
    }

    internal fun registerFragmentResult(requestFragment: Fragment): Maybe<FragmentResult> {
        val requestCode = RequestCodeGenerator.generate()

        val subject = PublishSubject.create<FragmentResult>()
        subjects.put(requestCode, subject)

        requestFragment.setTargetFragment(this, requestCode)

        return subject
                .take(1)
                .singleElement()
    }

    internal fun <T> registerFragmentResultCustom(requestFragment: Fragment): Maybe<T> {
        val requestCode = RequestCodeGenerator.generate()

        val subject = PublishSubject.create<T>()
        subjects.put(requestCode, subject)

        requestFragment.setTargetFragment(this, requestCode)

        return subject
                .take(1)
                .singleElement()
    }

    private fun handleActivityResult(requestCode: Int,
                                     resultCode: Int,
                                     data: Intent?) {
        val subject = subjects.remove(requestCode) as PublishSubject<FragmentResult>? ?: return
        subject.onNext(FragmentResult(requestCode, resultCode, data))
        subject.onComplete()
    }

    private fun <T> handleFragmentResult(requestCode: Int,
                                         result: T) {
        val subject = subjects.remove(requestCode) as PublishSubject<T>? ?: return
        subject.onNext(result)
        subject.onComplete()
    }

    companion object {
        private const val TAG_FRAGMENT = "com.ivianuu.rxfragmentresult.RxFragmentResultFragment"

        internal fun get(fm: FragmentManager): RxFragmentResultFragment {
            var fragment = fm.findFragmentByTag(TAG_FRAGMENT) as RxFragmentResultFragment?
            if (fragment == null) {
                fragment = RxFragmentResultFragment()
                fm.beginTransaction()
                        .add(fragment, TAG_FRAGMENT)
                        .commitAllowingStateLoss()
                fm.executePendingTransactions()
            }

            return fragment
        }

    }
}

/**
 * Generates request codes
 */
private object RequestCodeGenerator {

    private val seed = AtomicInteger(500)

    fun generate() = seed.incrementAndGet()
}