package org.knowledgeroot.app.util;

import java.util.ArrayList;
import java.util.List;

public interface Converter<A, B> {
    B convertAtoB(A from);

    default List<B> convertAtoB(List<A> list) {
        List<B> to = new ArrayList<>();

        if(list == null)
            return to;

        for(A from : list)
            to.add(convertAtoB(from));

        return to;
    };

    A convertBtoA(B from);
    default List<A> convertBtoA(List<B> list) {
        List<A> to = new ArrayList<>();

        if(list == null)
            return to;

        for(B from : list)
            to.add(convertBtoA(from));

        return to;
    };
}
