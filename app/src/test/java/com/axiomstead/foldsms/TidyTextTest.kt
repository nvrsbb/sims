package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

class TidyTextTest {
    private fun tidy(s: String) = tidyPunctuation(s).text

    @Test fun addsOneSpaceAfterCommonPunctuationWhenALetterFollows() {
        assertEquals("Hi, Ana. How are you? Fine! Thanks", tidy("Hi,Ana.How are you?Fine!Thanks"))
        assertEquals("Note: buy rice", tidy("Note:buy rice"))
        assertEquals("one; two", tidy("one;two"))
    }

    @Test fun existingSpacesAreNotDoubled() {
        assertEquals("Hi, Ana. Ok", tidy("Hi, Ana. Ok"))
        assertEquals("a,  b", tidy("a,  b")) // spacing the owner typed on purpose is kept
    }

    @Test fun capitalizesTheLetterAfterASentenceEnd() {
        assertEquals("Ok. Then we go! Really? Yes", tidy("Ok. then we go! really? yes"))
        assertEquals("Ok.\nThen", tidy("Ok.\nthen"))
        assertEquals("Hello world", tidy("Hello world")) // the start of the message is left alone
    }

    @Test fun numbersTimesAndMoneyAreUntouched() {
        assertEquals("P1,000.50 at 10:30 and 3.5 km", tidy("P1,000.50 at 10:30 and 3.5 km"))
        assertEquals("Room 3. a", tidy("Room 3. a")) // a list number is not a sentence end
    }

    @Test fun webAndEmailAddressesAreLeftAlone() {
        assertEquals("See example.com and www.site.ph/a,b or me@mail.com.Ok", tidy("See example.com and www.site.ph/a,b or me@mail.com.Ok"))
        assertEquals("Go to https://x.io/a. then home", tidy("Go to https://x.io/a. then home"))
        assertEquals("Visit example.com. Then go", tidy("Visit example.com. then go"))
    }

    @Test fun initialsEllipsesEmoticonsAndAbbreviationsAreNotBroken() {
        assertEquals("Use e.g. rice", tidy("Use e.g. rice"))
        assertEquals("U.S.A is far", tidy("U.S.A is far"))
        assertEquals("Wait... what", tidy("Wait... what"))
        assertEquals("Wait... ok", tidy("Wait...ok"))
        assertEquals("Fun :D yes ;) ok", tidy("Fun :D yes ;) ok"))
        assertEquals("Buy rice, etc. and eggs", tidy("Buy rice, etc. and eggs"))
        assertEquals("Hello Mr. cruz", tidy("Hello Mr. cruz"))
    }

    @Test fun taglishAndAccentedLettersWork() {
        assertEquals("Salamat po. Ingat, ha", tidy("Salamat po.ingat,ha"))
        assertEquals("Ola, Ñandú", tidy("Ola,Ñandú"))
    }

    @Test fun theSelectionFollowsItsText() {
        val t = tidyPunctuation("Hi,Ana. ok", 10, 10)
        assertEquals("Hi, Ana. Ok", t.text)
        assertEquals(11, t.start); assertEquals(11, t.end)
        val inside = tidyPunctuation("Hi,Ana", 2, 2) // cursor before the comma stays before it
        assertEquals(2, inside.start)
    }

    @Test fun whileTypingOnlyAPauseTriggersAndDeletingIsNeverUndone() {
        assertEquals(Tidied("Hi,An", 5, 5), tidyWhileTyping("Hi,A", "Hi,An", 5, 5)) // mid-word: untouched
        val paused = tidyWhileTyping("Hi,Ana", "Hi,Ana ", 7, 7)
        assertEquals("Hi, Ana ", paused.text); assertEquals(8, paused.start)
        assertEquals(Tidied("Hi,Ana", 6, 6), tidyWhileTyping("Hi,Ana ", "Hi,Ana", 6, 6)) // deleting the space
    }

    @Test fun tidyingTwiceChangesNothingMore() {
        val once = tidy("Hi,Ana.ok then.go")
        assertEquals(once, tidy(once))
    }
}
