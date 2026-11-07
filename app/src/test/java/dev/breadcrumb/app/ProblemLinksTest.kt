package dev.breadcrumb.app

import org.junit.Assert.*
import org.junit.Test

class ProblemLinksTest {
    @Test fun cleaningRemovesTrackingButKeepsRequiredQueryValuesEncoded() {
        val original = "https://example.com/problem?id=a%26b&utm_source=chat&lang=c%2B%2B&fbclid=123#section"
        assertEquals("https://example.com/problem?id=a%26b&lang=c%2B%2B", ProblemLinks.clean(original))
        assertEquals("https://example.com/problem?id=a%26b&utm_source=chat&lang=c%2B%2B&fbclid=123", ProblemLinks.normalize(original))
    }

    @Test fun cleaningPopularSitesPreservesProblemIdentifiers() {
        assertEquals("https://www.geeksforgeeks.org/problems/two-sum1234/1", ProblemLinks.clean("https://practice.geeksforgeeks.org/problems/two-sum1234/1?utm_source=test"))
        assertEquals("https://www.codechef.com/problems/FLOW001", ProblemLinks.clean("https://www.codechef.com/START200/problems/FLOW001?tab=statement"))
        assertEquals("https://atcoder.jp/contests/abc100/tasks/abc100_a", ProblemLinks.clean("https://atcoder.jp/contests/abc100/tasks/abc100_a?lang=en"))
        assertEquals("https://www.hackerrank.com/challenges/two-sum/problem", ProblemLinks.clean("https://www.hackerrank.com/challenges/two-sum/problem?isFullScreen=true"))
        assertEquals("https://codeforces.com/gym/123456/problem/A", ProblemLinks.clean("https://codeforces.com/gym/123456/problem/A?locale=en"))
    }

    @Test fun cleaningIsIdempotentAndLeavesFunctionalParametersOnUnknownSites() {
        val url = "https://example.com/problems/task?id=17&source=archive&utm_medium=share"
        val clean = ProblemLinks.clean(url)
        assertEquals("https://example.com/problems/task?id=17&source=archive", clean)
        assertEquals(clean, ProblemLinks.clean(clean))
        assertEquals("https://leetcode.com/problems/two-sum/", ProblemLinks.clean("https://leetcode.com/problems/two-sum/description/?envId=list"))
    }
    @Test fun siteCategoryFallsBackForEntriesCreatedBeforeTheDropdown() {
        assertEquals(ProblemSite.LEETCODE, ProblemSite.fromStored(null, "https://leetcode.com/problems/two-sum/"))
        assertEquals(ProblemSite.CODEFORCES, ProblemSite.fromStored("", "https://codeforces.com/contest/2123/problem/A"))
        assertEquals(ProblemSite.GEEKSFORGEEKS, ProblemSite.fromStored("unknown-future-site", "https://geeksforgeeks.org/problems/example/1"))
    }

    @Test fun explicitSiteCategoryOverridesTheUrlAndSurvivesTrailingNewline() {
        assertEquals(ProblemSite.CODECHEF, ProblemSite.fromStored("CODECHEF\n", "https://example.com/problem"))
        assertEquals(ProblemSite.OTHER, ProblemSite.fromStored("OTHER", "https://leetcode.com/problems/two-sum/"))
    }

    @Test fun popularSitesAreRecognizedWithOptionalSchemeAndMixedCaseHost() {
        assertEquals(ProblemSite.CODECHEF, ProblemSite.fromUrl("www.codechef.com/problems/FLOW001"))
        assertEquals(ProblemSite.ATCODER, ProblemSite.fromUrl("https://WWW.ATCODER.JP/contests/abc100/tasks/abc100_a"))
        assertEquals(ProblemSite.HACKERRANK, ProblemSite.fromUrl("https://www.hackerrank.com/challenges/example/problem"))
        assertEquals(ProblemSite.OTHER, ProblemSite.fromUrl("https://codechef.com.other.example/problem"))
    }
    @Test fun codeforcesContestAndProblemsetLinksReferToSameProblem() {
        val url = "https://codeforces.com/contest/2123/problem/A?locale=en"
        assertEquals("https://codeforces.com/problemset/problem/2123/A", ProblemLinks.normalize(url))
        assertTrue(ProblemLinks.isSupportedProblem(url))
        assertEquals("Codeforces 2123 A", ProblemLinks.suggestedTitle(url))
        assertEquals(ProblemSite.CODEFORCES, ProblemSite.fromUrl(url))
        assertFalse(ProblemLinks.isSupportedProblem("https://codeforces.com/contest/2123"))
    }
    @Test fun leetcodeLinksLoseTrackingAndSolutionSuffixes() {
        assertEquals("https://leetcode.com/problems/two-sum/",
            ProblemLinks.normalize(" https://leetcode.com/problems/two-sum/solutions/123?envId=list#comments "))
    }

    @Test fun linksWithoutSchemeAndGfgProblemNumbersAreSupported() {
        assertEquals("https://leetcode.com/problems/two-sum/", ProblemLinks.normalize("leetcode.com/problems/two-sum"))
        assertTrue(ProblemLinks.isSupportedProblem("https://www.geeksforgeeks.org/problems/array-subset-of-another-array2317/1"))
        assertEquals("Array Subset Of Another Array2317",
            ProblemLinks.suggestedTitle("https://www.geeksforgeeks.org/problems/array-subset-of-another-array2317/1"))
    }

    @Test fun titlesComeFromProblemSlugRatherThanDescriptionPage() {
        assertEquals("Add Two Numbers", ProblemLinks.suggestedTitle("https://leetcode.com/problems/add-two-numbers/description/"))
        assertEquals("Some Problem", ProblemLinks.suggestedTitle("https://example.com/some-problem.html"))
        assertEquals("Two Sum", ProblemLinks.cleanTitle("Two Sum - LeetCode"))
    }

    @Test fun lookupFiltersOutSimilarDomainsAndNonProblemPages() {
        assertFalse(ProblemLinks.isSupportedProblem("https://leetcode.com.evil.example/problems/two-sum"))
        assertFalse(ProblemLinks.isSupportedProblem("https://leetcode.com/discuss/interview-question/123"))
        assertFalse(ProblemLinks.isSupportedProblem("https://geeksforgeeks.org/"))
    }

    @Test fun invalidAndNonWebLinksAreRejected() {
        listOf("", "javascript://example.com/a", "file:///a.jpg", "hello world", "https://user:password@example.com/a").forEach { value ->
            assertTrue("Expected rejection for $value", runCatching { ProblemLinks.normalize(value) }.isFailure)
        }
    }

    @Test fun titleSearchAcceptsUnorderedWordsAndPunctuation() {
        assertTrue(ProblemLinks.matches("Two Sum II — Input Array Is Sorted", "sorted two"))
        assertTrue(ProblemLinks.matches("Two-Sum", "two sum"))
        assertTrue(ProblemLinks.matches("Café", "cafe"))
        assertTrue(ProblemLinks.matches("Two Sum", ""))
        assertFalse(ProblemLinks.matches("Two Sum", "two hash"))
    }

    @Test fun scanPrefersNumberedTitleAndKeepsFallbackShort() {
        assertEquals("1. Two Sum", ProblemLinks.scanQuery("Description\n1. Two Sum\nGiven an array of integers nums"))
        assertEquals("Given an array of integers nums", ProblemLinks.scanQuery("Description\nGiven an array of integers nums\nEasy\nSubmissions"))
        assertTrue(ProblemLinks.scanQuery("some words ".repeat(100)).length <= 240)
    }

    @Test fun shareTextCanIncludeSurroundingWords() {
        assertEquals("https://leetcode.com/problems/two-sum/",
            ProblemLinks.extractSharedLink("Check this problem https://leetcode.com/problems/two-sum/."))
        assertNull(ProblemLinks.extractSharedLink("Just a title"))
    }
}
