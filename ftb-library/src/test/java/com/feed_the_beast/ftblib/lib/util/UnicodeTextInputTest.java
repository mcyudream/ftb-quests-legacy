package com.feed_the_beast.ftblib.lib.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnicodeTextInputTest {
    @Test void commitsChineseAndPunctuationImmediately() {
        var input = new UnicodeTextInput();
        assertEquals("中", input.accept('中'));
        assertEquals("文", input.accept('文'));
        assertEquals("！", input.accept('！'));
    }
    @Test void commitsSupplementaryHanAsOneCompleteCharacter() {
        var input = new UnicodeTextInput();
        char[] pair = Character.toChars(0x20000);
        assertEquals("", input.accept(pair[0]));
        assertEquals("𠀀", input.accept(pair[1]));
        assertEquals("", input.accept(pair[1]));
    }
    @Test void focusChangesDoNotLeakUnfinishedCompositionIntoAnotherField() {
        var input = new UnicodeTextInput();
        char[] pair = Character.toChars(0x20000);
        input.accept(pair[0]); input.reset();
        assertEquals("", input.accept(pair[1]));
        assertEquals("新", input.accept('新'));
        input.accept(pair[0]);
        assertEquals("框", input.accept('框'));
    }
}
