/*
 * Copyright 2021 SIA Joom
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

package com.joom.paranoid.processor

import com.joom.grip.mirrors.getObjectType
import com.joom.paranoid.DeobfuscatorHelper
import com.joom.paranoid.Obfuscate

val OBJECT_TYPE = getObjectType<Any>()
val OBFUSCATE_TYPE = getObjectType<Obfuscate>()
val DEOBFUSCATOR_HELPER_TYPE = getObjectType<DeobfuscatorHelper>()
