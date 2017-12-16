package org.knowledgeroot.app.acl.resource;

public class BaseResource implements ResourceInterface {
	private String resourceId;

	public BaseResource() {

	}

	public BaseResource(String resourceId) {
		this.resourceId = resourceId;
	}

	@Override
	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}
}
