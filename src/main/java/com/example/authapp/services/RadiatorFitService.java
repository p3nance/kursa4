package com.example.authapp.services;

import com.example.authapp.domain.*;
import java.util.*;
import static com.example.authapp.domain.ComponentSpecs.*;
import static com.example.authapp.services.CompatibilityService.Status.*;

/** Assesses every documented alternative; missing locations are not silently ruled out. */
final class RadiatorFitService {
    private record Candidate(CompatibilityService.Status status,String message) {}
    CompatibilityService.Finding assess(CatalogComponent chassis,CatalogComponent cooler) {
        Integer nominal=verified(cooler,RADIATOR_NOMINAL_MM);
        String nominalNote=nominal==null?"":" Номинальный класс радиатора: "+nominal+" мм; это не фактический размер радиатора.";
        var matrix=chassis==null?Optional.<Fact<RadiatorMountMatrix>>empty():chassis.specs().fact(RADIATOR_MOUNTS).filter(Fact::verified);
        if(matrix.isEmpty())return finding(INSUFFICIENT_DATA,"Размещение радиатора СЖО не подтверждено."+nominalNote
                +" Нужны проверенные позиции крепления и фактические размеры радиатора с вентиляторами, а также ограничения корпуса; лимит высоты воздушного кулера этого не проверяет.");
        List<Candidate> candidates=new ArrayList<>();
        for(var rule:matrix.get().value().rules())candidates.add(assess(rule,cooler,nominal));
        var fit=candidates.stream().filter(c->c.status()==COMPATIBLE).findFirst();
        if(fit.isPresent())return finding(COMPATIBLE,"Проверка размещения радиатора пройдена: "+fit.get().message()
                +". Проверены номинал, фактические размеры и один штатный слой вентиляторов; остальные проверки сборки независимы.");
        boolean complete=matrix.get().value().coverage()==RadiatorMountMatrix.Coverage.COMPLETE;
        if(complete && candidates.stream().allMatch(c->c.status()==INCOMPATIBLE))
            return finding(INCOMPATIBLE,"Радиатор СЖО не подходит ни к одной штатной позиции корпуса."+nominalNote
                    +" "+join(candidates));
        return finding(INSUFFICIENT_DATA,"Размещение радиатора СЖО не подтверждено."+nominalNote
                +(complete?"":" Перечень позиций частичный: неописанные позиции не исключены.")
                +" "+join(candidates));
    }
    private Candidate assess(RadiatorMountMatrix.Rule rule,CatalogComponent cooler,Integer nominal) {
        String classes=String.join("/",rule.nominalSizesMm().stream().sorted().map(Object::toString).toList());
        String prefix=rule.position().label()+" ("+classes+" мм): ";
        if(nominal!=null && !rule.nominalSizesMm().contains(nominal))
            return new Candidate(INCOMPATIBLE,prefix+"класс "+nominal+" мм не поддерживается");
        Integer length=verified(cooler,RADIATOR_LENGTH_MM),width=verified(cooler,RADIATOR_WIDTH_MM);
        Integer thickness=verified(cooler,RADIATOR_THICKNESS_MM),fan=verified(cooler,RADIATOR_FAN_THICKNESS_MM);
        Long stack=thickness==null||fan==null?null:thickness.longValue()+fan.longValue();
        if(exceeds(length,rule.maxLengthMm()))return new Candidate(INCOMPATIBLE,prefix+"длина "+length+" мм превышает "+rule.maxLengthMm()+" мм");
        if(exceeds(width,rule.maxWidthMm()))return new Candidate(INCOMPATIBLE,prefix+"ширина "+width+" мм превышает "+rule.maxWidthMm()+" мм");
        if(exceeds(thickness,rule.radiatorThicknessUpperBoundMm()))return new Candidate(INCOMPATIBLE,prefix+"толщина самого радиатора "+thickness
                +" мм превышает подтверждённую верхнюю границу "+rule.radiatorThicknessUpperBoundMm()+" мм");
        if(stack!=null && rule.maxAssemblyThicknessMm()!=null && stack>rule.maxAssemblyThicknessMm())
            return new Candidate(INCOMPATIBLE,prefix+"толщина радиатора с вентиляторами "+stack+" мм превышает "+rule.maxAssemblyThicknessMm()+" мм");
        List<String> missing=new ArrayList<>();
        if(nominal==null)missing.add("номинальный класс");
        if(length==null||rule.maxLengthMm()==null)missing.add("фактическая длина и её предел");
        if(width==null||rule.maxWidthMm()==null)missing.add("фактическая ширина и её предел");
        if(stack==null||rule.maxAssemblyThicknessMm()==null)missing.add("суммарная толщина радиатора с вентиляторами и её предел");
        missing.addAll(rule.unresolvedConditions());
        String boundNote=rule.radiatorThicknessUpperBoundMm()==null?"":". Граница толщины самого радиатора "+rule.radiatorThicknessUpperBoundMm()+" мм не заменяет проверку всей сборки с вентиляторами";
        if(!missing.isEmpty())return new Candidate(INSUFFICIENT_DATA,prefix+"требуют проверки: "+String.join(", ",missing)+boundNote);
        return new Candidate(COMPATIBLE,prefix+length+"×"+width+"×"+stack+" мм с вентиляторами");
    }
    private static boolean exceeds(Integer actual,Integer limit) { return actual!=null && limit!=null && actual>limit; }
    private static Integer verified(CatalogComponent part,Attribute<Integer> attribute) {
        return part.specs().fact(attribute).filter(Fact::verified).map(Fact::value).orElse(null);
    }
    private static String join(List<Candidate> candidates) { return String.join("; ",candidates.stream().map(Candidate::message).toList()); }
    private static CompatibilityService.Finding finding(CompatibilityService.Status status,String message) {
        return new CompatibilityService.Finding("radiator_fit",status,message.strip());
    }
}
