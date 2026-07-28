/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vc.tbc.bq.jdbc.storage;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides, once per JVM, whether Arrow can actually allocate memory here.
 *
 * <p>
 * Arrow needs {@code --add-opens=java.base/java.nio=ALL-UNNAMED} on JDK 16+.
 * Without it {@code MemoryUtil} fails to initialise and every allocation
 * throws. A JDBC driver cannot set its own JVM flags, and the environment that
 * matters most here — IntelliJ, which runs drivers in a separate process — does
 * not pass that flag by default. So the Storage Read API path has to be able to
 * answer "is Arrow usable?" before committing to it, and fall back to the REST
 * path when the answer is no. A missing flag then costs throughput, not
 * correctness.
 *
 * <p>
 * Three details of the failure mode drive this implementation, all of them
 * measured rather than assumed:
 *
 * <ul>
 * <li><b>The probe must allocate.</b> Constructing a {@link RootAllocator}
 * succeeds even when the flag is absent, because nothing touches
 * {@code MemoryUtil} until a buffer is actually allocated. A probe that only
 * builds an allocator reports success and then dies mid-{@code ResultSet}.
 * <li><b>It must catch {@link Throwable}.</b> The first attempt throws
 * {@code ExceptionInInitializerError}; every attempt afterwards throws
 * {@code NoClassDefFoundError}, because the class is left in a failed
 * initialisation state. Catching either alone leaves the other unhandled. The
 * same catch also covers Arrow being absent from the classpath entirely, which
 * is possible for anyone depending on the slim jar without its Arrow modules.
 * <li><b>The verdict is stable</b>, so it is computed once and cached.
 * Repeating a failing allocation per statement would be pure overhead.
 * </ul>
 *
 * @since 2.4.0
 */
public final class ArrowSupport {

	private static final Logger logger = LoggerFactory.getLogger(ArrowSupport.class);

	/** The flag Arrow needs, quoted in the log so users can copy it. */
	public static final String REQUIRED_JVM_FLAG = "--add-opens=java.base/java.nio=ALL-UNNAMED";

	/**
	 * Holder idiom: the probe runs on first use, not at class-load time, and the
	 * JVM guarantees it runs exactly once.
	 */
	private static final class Probe {
		static final boolean USABLE = probe();

		private Probe() {
		}

		@SuppressWarnings("PMD.AvoidCatchingThrowable") // ExceptionInInitializerError/NoClassDefFoundError are Errors;
														// catching them is the whole point of this probe
		private static boolean probe() {
			try (RootAllocator allocator = new RootAllocator(Long.MAX_VALUE);
					BigIntVector vector = new BigIntVector("probe", allocator)) {
				// Allocating is what touches MemoryUtil. Constructing the allocator is not
				// enough — see the class javadoc.
				vector.allocateNew(1);
				vector.setSafe(0, 1L);
				vector.setValueCount(1);
				return vector.get(0) == 1L;
			} catch (Throwable t) {
				logger.info(
						"Apache Arrow is not usable in this JVM, so the BigQuery Storage Read API path is "
								+ "unavailable and queries will use the standard REST result path instead. "
								+ "To enable it, start the JVM with {} (in IntelliJ/DataGrip: Data Sources -> "
								+ "Advanced -> VM options). Cause: {}: {}",
						REQUIRED_JVM_FLAG, t.getClass().getName(), t.getMessage());
				return false;
			}
		}
	}

	private ArrowSupport() {
	}

	/**
	 * Whether Arrow can allocate memory in this JVM.
	 *
	 * <p>
	 * Probed once on first call and cached; safe to call per statement.
	 *
	 * @return true if the Storage Read API path can decode rows here
	 */
	public static boolean isUsable() {
		return Probe.USABLE;
	}
}
