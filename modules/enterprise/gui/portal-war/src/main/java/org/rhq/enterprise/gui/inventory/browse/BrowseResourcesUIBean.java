package org.rhq.enterprise.gui.inventory.browse;

import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.component.html.HtmlInputText;
import javax.faces.model.DataModel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.criteria.ResourceCriteria;
import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.core.domain.resource.composite.ResourceComposite;
import org.rhq.core.domain.search.SavedSearch;
import org.rhq.core.domain.search.SearchSubsystem;
import org.rhq.core.domain.search.SearchSuggestion;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.core.gui.util.FacesContextUtility;
import org.rhq.core.gui.util.StringUtility;
import org.rhq.core.util.IntExtractor;
import org.rhq.enterprise.gui.common.framework.PagedDataTableUIBean;
import org.rhq.enterprise.gui.common.paging.PageControlView;
import org.rhq.enterprise.gui.common.paging.ResourceNameDisambiguatingPagedListDataModel;
import org.rhq.enterprise.gui.util.EnterpriseFacesContextUtility;
import org.rhq.enterprise.server.resource.ResourceManagerLocal;
import org.rhq.enterprise.server.search.SavedSearchManagerLocal;
import org.rhq.enterprise.server.search.execution.SearchAssistManager;
import org.rhq.enterprise.server.util.HibernatePerformanceMonitor;
import org.rhq.enterprise.server.util.LookupUtil;

public class BrowseResourcesUIBean extends PagedDataTableUIBean {
    protected final Log log = LogFactory.getLog(BrowseResourcesUIBean.class);

    public static final String MANAGED_BEAN_NAME = "BrowseResourcesUIBean";

    private String search;
    private HtmlInputText searchInput;
    private ResourceCategory category;

    private ResourceManagerLocal resourceManager = LookupUtil.getResourceManager();
    private SavedSearchManagerLocal savedSearchManager = LookupUtil.getSavedSearchManager();

    private static final IntExtractor<ResourceComposite> RESOURCE_ID_EXTRATOR = new IntExtractor<ResourceComposite>() {
        public int extract(ResourceComposite object) {
            return object.getResource().getId();
        }
    };

    public BrowseResourcesUIBean() {
        String subtab = FacesContextUtility.getOptionalRequestParameter("subtab", "").toLowerCase();
        if (subtab.equals("platform")) {
            category = ResourceCategory.PLATFORM;
        } else if (subtab.equals("server")) {
            category = ResourceCategory.SERVER;
        } else if (subtab.equals("service")) {
            category = ResourceCategory.SERVICE;
        }

        String searchId = FacesContextUtility.getOptionalRequestParameter("searchId");
        if (searchId != null) {
            SavedSearch savedSearch = savedSearchManager.getSavedSearchById(getSubject(), Integer.valueOf(searchId));
            search = savedSearch.getPattern();
        } else {
            search = FacesContextUtility.getOptionalRequestParameter("search");
        }
    }

    public String getSearch() {
        return this.search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public ResourceCategory getCategory() {
        return this.category;
    }

    @Override
    public synchronized DataModel getDataModel() {
        if (dataModel == null) {
            dataModel = new ResultsDataModel(PageControlView.BrowseResources, MANAGED_BEAN_NAME);
        }
        return dataModel;
    }

    private class ResultsDataModel extends ResourceNameDisambiguatingPagedListDataModel<ResourceComposite> {

        public ResultsDataModel(PageControlView view, String beanName) {
            super(view, beanName, true);
        }

        public PageList<ResourceComposite> fetchDataForPage(PageControl pc) {
            String search = getSearch();
            ResourceCategory category = getCategory();

            ResourceCriteria criteria = new ResourceCriteria();
            criteria.setPageControl(pc);
            criteria.addFilterResourceCategory(category);
            if (search != null && !search.trim().equals("")) {
                criteria.setSearchExpression(search);
            }
            criteria.fetchParentResource(true);

            PageList<ResourceComposite> results;
            results = resourceManager.findResourceCompositesByCriteria(getSubject(), criteria);
            return results;
        }

        protected IntExtractor<ResourceComposite> getResourceIdExtractor() {
            return RESOURCE_ID_EXTRATOR;
        }
    }

    public String uninventorySelectedResources() {
        try {
            Subject subject = EnterpriseFacesContextUtility.getSubject();

            String[] selectedResources = getSelectedItems();
            int[] resourceIds = StringUtility.getIntArray(selectedResources);

            resourceManager.uninventoryResources(subject, resourceIds);
            FacesContextUtility.addMessage(FacesMessage.SEVERITY_INFO, "Uninventoried selected resources");
        } catch (Exception e) {
            FacesContextUtility.addMessage(FacesMessage.SEVERITY_ERROR, "Failed to uninventory selected resources", e);
        }

        return "success";
    }

    private String[] getSelectedItems() {
        return FacesContextUtility.getRequest().getParameterValues("selectedItems");
    }

    SearchAssistManager searchAssist = new SearchAssistManager(getSubject(), SearchSubsystem.RESOURCE);

    public List<SearchSuggestion> autocomplete(Object suggest) {
        String currentInputText = (String) suggest;

        // assume caret at the end of the input
        long id = HibernatePerformanceMonitor.get().start();
        List<SearchSuggestion> suggestions = searchAssist.getSuggestions(currentInputText, currentInputText.length());
        HibernatePerformanceMonitor.get().stop(id, "ResourceSuggestions");
        return suggestions;
    }

    public HtmlInputText getSearchInput() {
        return searchInput;
    }

    public void setSearchInput(HtmlInputText searchInput) {
        this.searchInput = searchInput;
    }

}
