package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CommandTemplatesTest
{
    @Test void preservesLegacySyntaxAndLiteralPercentDollarAndBackslash()
    {
        assertEquals("say A$\\B A$\\B 100% {unknown} @players", CommandTemplates.expand(
                "say @p {p} 100% {unknown} @players", Map.of("p", "A$\\B")));
    }
}
