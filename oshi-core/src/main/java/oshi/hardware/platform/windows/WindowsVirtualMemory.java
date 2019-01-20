/**
 * OSHI (https://github.com/oshi/oshi)
 *
 * Copyright (c) 2010 - 2019 The OSHI Project Team:
 * https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.hardware.platform.windows;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Kernel32; // NOSONAR squid:S1191
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiResult;

import oshi.data.windows.PerfCounterQuery;
import oshi.data.windows.PerfCounterQuery.PdhCounterProperty;
import oshi.hardware.common.AbstractVirtualMemory;
import oshi.util.platform.windows.WmiQueryHandler;
import oshi.util.platform.windows.WmiUtil;

/**
 * Memory obtained from WMI
 */
public class WindowsVirtualMemory extends AbstractVirtualMemory {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(WindowsVirtualMemory.class);

    private transient PerfCounterQuery<PageSwapProperty> memoryPerfCounters = new PerfCounterQuery<>(
            PageSwapProperty.class, "Memory", "Win32_PerfRawData_PerfOS_Memory");

    private final transient WmiQueryHandler wmiQueryHandler = WmiQueryHandler.createInstance();

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSwapUsed() {
        if (this.swapUsed < 0) {
            updateSwapUsed();
        }
        return this.swapUsed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSwapTotal() {
        if (this.swapTotal < 0) {
            PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
            if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
                LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
                return 0L;
            }
            this.swapTotal = perfInfo.PageSize.longValue()
                    * (perfInfo.CommitLimit.longValue() - perfInfo.PhysicalTotal.longValue());
        }
        return this.swapTotal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSwapPagesIn() {
        if (this.swapPagesIn < 0) {
            updateSwapInOut();
        }
        return this.swapPagesIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSwapPagesOut() {
        if (this.swapPagesOut < 0) {
            updateSwapInOut();
        }
        return this.swapPagesOut;
    }

    private void updateSwapInOut() {
        Map<PageSwapProperty, Long> valueMap = this.memoryPerfCounters.queryValues();
        this.swapPagesIn = valueMap.getOrDefault(PageSwapProperty.PAGESINPUTPERSEC, 0L);
        this.swapPagesOut = valueMap.getOrDefault(PageSwapProperty.PAGESOUTPUTPERSEC, 0L);
    }

    private void updateSwapUsed() {
        WmiQuery<PagingPercentProperty> pagingQuery = new WmiQuery<>("Win32_PerfRawData_PerfOS_PagingFile",
                PagingPercentProperty.class);
        WmiResult<PagingPercentProperty> paging = this.wmiQueryHandler.queryWMI(pagingQuery);
        if (paging.getResultCount() > 0) {
            this.swapUsed = WmiUtil.getUint32asLong(paging, PagingPercentProperty.PERCENTUSAGE, 0) * getSwapTotal()
                    / WmiUtil.getUint32asLong(paging, PagingPercentProperty.PERCENTUSAGE_BASE, 0);
        }
    }

    /*
     * For swap file usage
     */
    enum PagingPercentProperty {
        PERCENTUSAGE, PERCENTUSAGE_BASE;
    }

    /*
     * For pages in/out
     */
    public enum PageSwapProperty implements PdhCounterProperty {
        PAGESINPUTPERSEC(null, "Pages Input/sec"), //
        PAGESOUTPUTPERSEC(null, "Pages Output/sec");

        private final String instance;
        private final String counter;

        PageSwapProperty(String instance, String counter) {
            this.instance = instance;
            this.counter = counter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getInstance() {
            return instance;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCounter() {
            return counter;
        }
    }
}
