package com.example.authapp.services;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit per-product price bounds, separate from the whole-computer budget. */
record ChatCatalogFilter(String query,BigDecimal minimum,boolean minimumInclusive,BigDecimal maximum,boolean maximumInclusive,Boolean inStock,String problem) {
    private static final String PREFIX="(?:не\\s+дороже|не\\s+дешевле|дешевле|дороже|до|от)";
    private static final String NUMBER="[-−]?\\s*\\d(?:[\\d ]*\\d)?(?:[.,]\\d+)?";
    private static final String UNIT="(?:(?:тыс(?:яч[аиу]?)?\\.?|[кk])(?:\\s*руб(?:лей|ля|ль)?)?|руб(?:лей|ля|ль)?|₽)";
    private static final Pattern START=Pattern.compile("(?iuU)\\b"+PREFIX+"\\s+[-−]?\\s*\\d");
    private static final Pattern BOUND=Pattern.compile("(?iuU)\\b("+PREFIX+")\\s+("+NUMBER+")\\s*("+UNIT+")(?![\\p{L}\\p{N}])");
    private static final Pattern STOCK=Pattern.compile("(?iuU)\\b(?:(не|нет)\\s+)?в\\s+наличии\\b");
    static boolean hasPriceSyntax(String raw){return raw!=null && START.matcher(raw).find();}
    boolean active(){return minimum!=null || maximum!=null || inStock!=null;}
    boolean accepts(ChatBotService.ProductInfo product) {
        int low=minimum==null?1:product.price().compareTo(minimum),high=maximum==null?-1:product.price().compareTo(maximum);
        return (minimum==null || low>0 || low==0&&minimumInclusive) && (maximum==null || high<0 || high==0&&maximumInclusive)
                && (inStock==null || inStock==(product.stock()>0));
    }
    static ChatCatalogFilter parse(String raw) {
        String text=Normalizer.normalize(Objects.toString(raw,""),Normalizer.Form.NFKC).replace('−','-').replace('ё','е');
        BigDecimal min=null,max=null;boolean minInclusive=true,maxInclusive=true;Boolean stock=null;String problem=null;
        var match=BOUND.matcher(text);var remaining=new StringBuffer();int count=0;
        while(match.find()) {
            count++;String prefix=match.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
            try {
                var value=new BigDecimal(match.group(2).replace(" ","").replace(',','.'));
                if(match.group(3)!=null && match.group(3).toLowerCase(Locale.ROOT).matches("(?:тыс.*|[кk](?:\\s*руб.*)?)"))value=value.multiply(new BigDecimal("1000"));
                value=value.setScale(2,java.math.RoundingMode.UNNECESSARY);
                if(value.signum()<0 || value.compareTo(new BigDecimal("100000000"))>0)throw new ArithmeticException();
                if(prefix.equals("от") || prefix.equals("дороже") || prefix.equals("не дешевле")) {
                    if(min!=null)problem="Уточните одну нижнюю границу цены товара.";
                    min=value;minInclusive=!prefix.equals("дороже");
                } else {
                    if(max!=null)problem="Уточните одну верхнюю границу цены товара.";
                    max=value;maxInclusive=!prefix.equals("дешевле");
                }
            } catch(ArithmeticException|NumberFormatException ex){problem="Укажите неотрицательную цену товара в рублях, с точностью до копеек и не выше 100 млн.";}
            match.appendReplacement(remaining," ");
        }
        match.appendTail(remaining);
        if(count>1 && Pattern.compile("(?iuU)\\b(?:или|либо)\\b").matcher(text).find())problem="Уточните один диапазон цены товара без «или/либо».";
        if(problem==null && min!=null && max!=null && (min.compareTo(max)>0 || min.compareTo(max)==0 && (!minInclusive||!maxInclusive)))problem="Нижняя граница цены не должна превышать верхнюю, а диапазон должен содержать допустимую цену.";
        if(hasPriceSyntax(text) && Pattern.compile("(?iuU)доллар|\\busd\\b|\\beur\\b|евро|[$€]").matcher(text).find())problem="Цены каталога заданы в рублях. Укажите предел цены товара в рублях.";
        if(count==0 && hasPriceSyntax(text) && problem==null && !text.toLowerCase(Locale.ROOT).contains("насколько"))problem="Уточните предел цены товара в рублях. Другие ограничения этой фразой пока не применены.";
        var availability=STOCK.matcher(remaining.toString());var query=new StringBuffer();
        while(availability.find()) {
            boolean next=availability.group(1)==null;
            if(stock!=null && stock!=next)problem="Уточните наличие: товары в наличии или отсутствующие.";
            stock=next;availability.appendReplacement(query," ");
        }availability.appendTail(query);
        String cleaned=query.toString().replaceAll("(?iuU)\\bтолько\\b", " ").replaceAll("\\s+"," ").replaceAll("^[ ,;:]+|[ ,;:.!?]+$", "").strip();
        cleaned=cleaned.replaceFirst("(?iuU)[,;]?\\s*пожалуйста$", "")
                .replaceFirst("(?iuU)[,;]?\\s*не\\s+меняя\\s+бюджет\\s+(?:пк|сборки)$", "").strip();
        return new ChatCatalogFilter(cleaned,min,minInclusive,max,maxInclusive,stock,problem);
    }
}
