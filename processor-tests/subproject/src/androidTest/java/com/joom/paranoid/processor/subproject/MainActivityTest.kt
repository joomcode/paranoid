/*
 *
 * Copyright 2020 SIA Joom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.joom.paranoid.processor.subproject

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {
  @Test
  fun testMainActivityIsProcessed() {
    val suffix = if (BuildConfig.DEBUG) "Debug" else "Release"
    val className = "com.joom.paranoid.Deobfuscator\$processortests\$subproject$$suffix"

    verifyDeobfuscatorGenerated(className = className, expectedString = "Subprojects:")
  }

  @Test
  fun testJavaModuleIsProcessed() {
    val className = "com.joom.paranoid.Deobfuscator\$processortests\$subprojectjava\$Subprojectjava"

    verifyDeobfuscatorGenerated(className = className, expectedString = "java")
  }

  @Test
  fun testAndroidModuleIsProcessed() {
    val suffix = if (com.joom.paranoid.processor.subproject.android.BuildConfig.DEBUG) "Debug" else "Release"
    val className = "com.joom.paranoid.Deobfuscator\$processortests\$subprojectandroid$$suffix"

    verifyDeobfuscatorGenerated(className = className, expectedString = "android")
  }

  @Test
  fun testTextIsDisplayed() {
    ActivityScenario.launch(MainActivity::class.java).use {
      Espresso.onView(ViewMatchers.withText("Subprojects: android, java"))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
  }

  private fun verifyDeobfuscatorGenerated(className: String, expectedString: String) {
    val deobfuscatorClass = Class.forName(className)
    val chunksField = deobfuscatorClass.getDeclaredField("chunks")

    chunksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val chunks = chunksField[null] as Array<String>

    Assert.assertNotNull(chunks)
    Assert.assertEquals(1, chunks.size)
    Assert.assertTrue(chunks[0].length > expectedString.length)
  }
}
