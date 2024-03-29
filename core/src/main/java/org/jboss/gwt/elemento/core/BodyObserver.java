package org.jboss.gwt.elemento.core;

import java.util.ArrayList;
import java.util.List;

import elemental2.dom.HTMLElement;
import elemental2.dom.MutationObserver;
import elemental2.dom.MutationObserverInit;
import elemental2.dom.MutationRecord;

import static elemental2.dom.DomGlobal.document;
import static java.util.stream.Collectors.toList;
import static org.jboss.gwt.elemento.core.Elements.asHtmlElement;
import static org.jboss.gwt.elemento.core.Elements.htmlElements;
import static org.jboss.gwt.elemento.core.Elements.stream;

final class BodyObserver {

    private static String ATTACH_UID_KEY = "on-attach-uid";
    private static String DETACH_UID_KEY = "on-detach-uid";

    private static List<ElementObserver> detachObservers = new ArrayList<>();
    private static List<ElementObserver> attachObservers = new ArrayList<>();
    private static boolean ready = false;

    private static void startObserving() {
        MutationObserver mutationObserver = new MutationObserver(
                (MutationRecord[] records, MutationObserver observer) -> {
                    for (MutationRecord record : records) {
                        onElementsRemoved(record);
                        onElementsAppended(record);
                    }
                    return null;
                });
/* not working with beta-1
        MutationObserverInit mutationObserverInit = MutationObserverInit.create();
        mutationObserverInit.setChildList(true);
        mutationObserverInit.setSubtree(true);
 */

        MutationObserverInit mutationObserverInit = new MutationObserverInit();
        mutationObserverInit.childList = true;
        mutationObserverInit.subtree = true;

        mutationObserver.observe(document.body, mutationObserverInit);
        ready = true;
    }

    private static void onElementsAppended(MutationRecord record) {
        List<ElementObserver> observed = new ArrayList<>();
        List<HTMLElement> addedElements = stream(record.addedNodes)
                .filter(htmlElements())
                .map(asHtmlElement())
                .collect(toList());

        for (ElementObserver elementObserver : attachObservers) {
            if (elementObserver.observedElement() == null) {
                observed.add(elementObserver);
            } else {
                if (addedElements.contains(elementObserver.observedElement()) ||
                        isChildOfObservedElement(addedElements, ATTACH_UID_KEY, elementObserver.attachId())) {
                    elementObserver.callback().onObserved(record);
                    elementObserver.observedElement().removeAttribute(ATTACH_UID_KEY);
                    observed.add(elementObserver);
                }
            }
        }
        attachObservers.removeAll(observed);
    }

    private static void onElementsRemoved(MutationRecord record) {
        List<ElementObserver> observed = new ArrayList<>();
        List<HTMLElement> removedElements = stream(record.removedNodes)
                .filter(htmlElements())
                .map(asHtmlElement())
                .collect(toList());

        for (ElementObserver elementObserver : detachObservers) {
            if (elementObserver.observedElement() == null) {
                observed.add(elementObserver);
            } else {
                if (removedElements.contains(elementObserver.observedElement()) ||
                        isChildOfObservedElement(removedElements, DETACH_UID_KEY, elementObserver.attachId())) {
                    elementObserver.callback().onObserved(record);
                    elementObserver.observedElement().removeAttribute(DETACH_UID_KEY);
                    observed.add(elementObserver);
                }
            }
        }
        detachObservers.removeAll(observed);
    }

    private static boolean isChildOfObservedElement(List<HTMLElement> elements, String attachUidKey, String attachId) {
        for (HTMLElement element : elements) {
            if (element.querySelector("[" + attachUidKey + "='" + attachId + "']") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the observer is already started, if not it will start it, then register and callback for when the
     * element is attached to the dom.
     */
    static void addAttachObserver(HTMLElement element, ObserverCallback callback) {
        if (!ready) {
            startObserving();
        }
        attachObservers.add(createObserver(element, callback, ATTACH_UID_KEY));
    }

    /**
     * Check if the observer is already started, if not it will start it, then register and callback for when the
     * element is removed from the dom.
     */
    static void addDetachObserver(HTMLElement element, ObserverCallback callback) {
        if (!ready) {
            startObserving();
        }
        detachObservers.add(createObserver(element, callback, DETACH_UID_KEY));
    }

    private static ElementObserver createObserver(HTMLElement element, ObserverCallback callback,
            String idAttributeName) {
        String elementId = element.getAttribute(idAttributeName);
        if (elementId == null) {
            element.setAttribute(idAttributeName, Elements.createDocumentUniqueId());
        }
        return new ElementObserver() {
            @Override
            public String attachId() {
                return element.getAttribute(idAttributeName);
            }

            @Override
            public HTMLElement observedElement() {
                return element;
            }

            @Override
            public ObserverCallback callback() {
                return callback;
            }
        };
    }

    private BodyObserver() {
    }


    private interface ElementObserver {

        String attachId();

        HTMLElement observedElement();

        ObserverCallback callback();
    }
}
