package com.clave.vault

import org.junit.Assert.*
import org.junit.Test

class VaultDocumentTest {
    private fun empty() = VaultDocument(7, listOf("Personal", "Trabajo"), emptyList())
    private fun entry() = Credential(title = "Example", username = "alice", password = "Secret123!", website = "https://example.com")

    @Test fun roundTripPreservesSecretsAndMetadata() {
        val input = empty().upsert(entry().copy(status = "pending", previousPassword = "old", appPackage = "browser", appSignature = "cert"))
        assertEquals(input, VaultDocument.fromJson(input.toJson()))
        assertEquals(7L, input.revision)
    }

    @Test fun editsAreImmutableAndDoNotDuplicateIds() {
        val original = empty()
        val item = entry()
        val added = original.upsert(item)
        val changed = added.upsert(item.copy(password = "changed"))
        assertTrue(original.credentials.isEmpty())
        assertEquals("Secret123!", added.credentials.single().password)
        assertEquals("changed", changed.credentials.single().password)
        assertEquals(1, changed.credentials.size)
        assertTrue(changed.delete(item.id).credentials.isEmpty())
    }

    @Test fun profileRenameUpdatesAccountsAndRejectsCollisions() {
        val document = empty().upsert(entry())
        val renamed = document.renameProfile("Personal", "Ocio")
        assertEquals("Ocio", renamed.credentials.single().profile)
        assertThrows(IllegalArgumentException::class.java) { document.addProfile(" personal ") }
        assertThrows(IllegalArgumentException::class.java) { document.renameProfile("Personal", "TRABAJO") }
        assertThrows(IllegalArgumentException::class.java) { document.deleteProfile("Personal") }
        val single = document.deleteProfile("Trabajo")
        assertThrows(IllegalArgumentException::class.java) { single.deleteProfile("Personal") }
    }

    @Test fun pendingChangesCanBeConfirmedOrRolledBack() {
        val item = entry().copy(status = "pending", previousPassword = "old")
        val document = empty().upsert(item)
        val confirmed = document.confirm(item.id).credentials.single()
        assertEquals("saved", confirmed.status)
        assertNull(confirmed.previousPassword)
        assertEquals(item.password, confirmed.password)
        val restored = document.restore(item.id).credentials.single()
        assertEquals("old", restored.password)
        assertEquals("saved", restored.status)
        assertNull(restored.previousPassword)
        assertEquals("old", document.upsert(item.copy(password = "another", previousPassword = null)).credentials.single().previousPassword)
    }

    @Test fun invalidInputIsRejectedBeforeWriting() {
        for (item in listOf(entry().copy(title = "x".repeat(101)), entry().copy(username = ""), entry().copy(password = ""),
            entry().copy(profile = "Missing"), entry().copy(website = "http://example.com"), entry().copy(website = "https://user@example.com"))) {
            assertThrows(IllegalArgumentException::class.java) { empty().upsert(item) }
        }
    }

    @Test fun optionalNameUsesWebsiteThenAppThenUsername() {
        assertEquals("example.com", empty().upsert(entry().copy(title = " ")).credentials.single().title)
        assertEquals("app.example", empty().upsert(entry().copy(title = "", website = "", appPackage = "app.example")).credentials.single().title)
        assertEquals("alice", empty().upsert(entry().copy(title = "", website = "")).credentials.single().title)
        assertEquals(100, empty().upsert(entry().copy(title = "", website = "", username = "a".repeat(200))).credentials.single().title.length)
        assertEquals("My label", empty().upsert(entry().copy(title = " My label ")).credentials.single().title)
    }

    @Test fun oldVaultsInheritColorsWithoutMigration() {
        val json = empty().upsert(entry()).toJson().apply { remove("profileColors") }
        json.getJSONArray("credentials").getJSONObject(0).remove("accent")
        val read = VaultDocument.fromJson(json)
        assertNull(read.credentials.single().accent)
        assertEquals(AccountAccent.SLATE, read.accountColor(read.credentials.single()))
        assertEquals(AccountAccent.ROSE, read.setProfileColor("Personal", AccountAccent.ROSE).accountColor(read.credentials.single()))
    }

    @Test fun colorsRoundTripRenameDeleteAndReset() {
        val item = entry().copy(accent = AccountAccent.AMBER)
        val colored = empty().upsert(item).setProfileColor("Personal", AccountAccent.VIOLET)
        assertEquals(colored, VaultDocument.fromJson(colored.toJson()))
        val renamed = colored.renameProfile("Personal", "Family")
        assertEquals(AccountAccent.VIOLET, renamed.profileColor("Family"))
        assertFalse(renamed.profileColors.containsKey("Personal"))
        // Old per-account overrides must never override the profile color.
        assertEquals(AccountAccent.VIOLET, renamed.accountColor(renamed.credentials.single()))
        val reset = renamed.upsert(renamed.credentials.single().copy(accent = null))
        assertEquals(AccountAccent.VIOLET, reset.accountColor(reset.credentials.single()))
        assertFalse(reset.delete(item.id).deleteProfile("Family").profileColors.containsKey("Family"))
        val automatic = empty().renameProfile("Personal", "Other")
        assertEquals(AccountAccent.SLATE, automatic.profileColor("Other"))
    }

    @Test fun unknownColorsFallBackAndBadProfileChangesAreRejected() {
        val json = empty().upsert(entry()).toJson()
        json.getJSONObject("profileColors").put("Personal", "INVALID")
        json.getJSONArray("credentials").getJSONObject(0).put("accent", "INVALID")
        val read = VaultDocument.fromJson(json)
        assertEquals(AccountAccent.SLATE, read.accountColor(read.credentials.single()))
        assertThrows(IllegalArgumentException::class.java) { read.setProfileColor("Missing", AccountAccent.BLUE) }
    }
}
