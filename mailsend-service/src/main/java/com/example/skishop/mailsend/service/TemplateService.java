package com.example.skishop.mailsend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

@Service
public class TemplateService {

    private final SpringTemplateEngine templateEngine;

    public TemplateService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedMailContent render(String templateName, Map<String, Object> variables) {
        var context = new Context(Locale.JAPAN);
        context.setVariables(variables);

        String html = templateEngine.process("mail/" + templateName, context);
        String plainText = toPlainText(html);

        return new RenderedMailContent(html, plainText);
    }

    private static String toPlainText(String html) {
        if (html == null) {
            return "";
        }

        var withoutScripts = html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ");

        var withoutTags = withoutScripts.replaceAll("(?s)<[^>]*>", " ");
        var unescaped = HtmlUtils.htmlUnescape(withoutTags);
        return unescaped.replaceAll("\\s+", " ").trim();
    }
}
