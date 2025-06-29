package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PageEditPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testEditPageWithPermissionChanges() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with permission changes");

        // Test editing a page with permission updates, deletions, and additions
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Updated Page Name")
                .param("content", "Updated page content")
                // Permission updates
                .param("permissionUpdates[1]", "view")
                .param("permissionUpdates[2]", "edit")
                // Permission deletions
                .param("permissionDeletions[]", "3")
                .param("permissionDeletions[]", "4")
                // Permission additions
                .param("permissionAdditions[0][roleType]", "user")
                .param("permissionAdditions[0][roleId]", "2")
                .param("permissionAdditions[0][permissionLevel]", "view")
                .param("permissionAdditions[1][roleType]", "group")
                .param("permissionAdditions[1][roleId]", "2")
                .param("permissionAdditions[1][permissionLevel]", "edit")
                .param("permissionAdditions[2][roleType]", "guest")
                .param("permissionAdditions[2][roleId]", "")
                .param("permissionAdditions[2][permissionLevel]", "view"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithOnlyPageContent() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with only content changes");

        // Test editing a page without permission changes
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Simple Page Update")
                .param("content", "Simple content update"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithOnlyPermissionUpdates() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with only permission updates");

        // Test editing a page with only permission updates
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Test Page")
                .param("content", "Test content")
                .param("permissionUpdates[1]", "none")
                .param("permissionUpdates[2]", "view"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithOnlyPermissionDeletions() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with only permission deletions");

        // Test editing a page with only permission deletions
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Test Page")
                .param("content", "Test content")
                .param("permissionDeletions[]", "5")
                .param("permissionDeletions[]", "6"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithOnlyPermissionAdditions() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with only permission additions");

        // Test editing a page with only permission additions
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Test Page")
                .param("content", "Test content")
                .param("permissionAdditions[0][roleType]", "user")
                .param("permissionAdditions[0][roleId]", "2")
                .param("permissionAdditions[0][permissionLevel]", "edit")
                .param("permissionAdditions[1][roleType]", "guest")
                .param("permissionAdditions[1][roleId]", "")
                .param("permissionAdditions[1][permissionLevel]", "view"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithGuestPermissions() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with guest permissions");

        // Test adding guest permissions (roleId should be null/empty)
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Test Page")
                .param("content", "Test content")
                .param("permissionAdditions[0][roleType]", "guest")
                .param("permissionAdditions[0][roleId]", "")
                .param("permissionAdditions[0][permissionLevel]", "view"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }

    @Test
    public void testEditPageWithNoPermissionParameters() throws Exception {
        System.out.println("[DEBUG_LOG] Testing page edit with no permission parameters");

        // Test that missing permission parameters don't cause issues (real-world scenario)
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/page/1/edit")
                .param("name", "Test Page")
                .param("content", "Test content"))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/ui/page/1?trigger=reload-sidebar"));
    }
}
