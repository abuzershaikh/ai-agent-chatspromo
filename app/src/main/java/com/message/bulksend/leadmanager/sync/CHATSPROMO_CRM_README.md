# Chatspromo CRM - Firestore Backup & Sync System

## 📋 Overview

This document outlines the complete backup and restore system for Chatspromo CRM (Lead Manager) module. The system provides real-time sync with Firestore, backing up all lead data, custom fields, products, follow-ups, payments, invoices, notes, timeline, and settings.

## 🗂️ Data Structure Analysis

### Database Entities to Backup

| Entity | Table Name | Description | Foreign Key |
|--------|------------|-------------|-------------|
| LeadEntity | `leads` | Core lead data with status, priority, tags | - |
| FollowUpEntity | `follow_ups` | Scheduled follow-ups | leadId → leads |
| ProductEntity | `products` | Products/Services catalog | - |
| ChatMessageEntity | `chat_messages` | WhatsApp chat history | leadId → leads |
| AutoAddSettingsEntity | `auto_add_settings` | Auto-add configuration | - |
| AutoAddKeywordRuleEntity | `auto_add_keyword_rules` | Keyword rules for auto-add | - |
| TimelineEntity | `timeline_entries` | Lead activity timeline | leadId → leads |
| NoteEntity | `lead_notes` | Lead notes with types | leadId → leads |
| CustomFieldDefinitionEntity | `custom_field_definitions` | Custom field schemas | - |
| CustomFieldValueEntity | `custom_field_values` | Custom field values per lead | leadId → leads |
| PaymentEntity | `payments` | Payment records | leadId → leads |
| InvoiceEntity | `invoices` | Invoice records | leadId → leads |

### Enums to Preserve

```kotlin
// Lead Status
enum class LeadStatus { NEW, INTERESTED, CONTACTED, QUALIFIED, CONVERTED, CUSTOMER, LOST }

// Lead Priority  
enum class LeadPriority { HIGH, MEDIUM, LOW }

// Follow-up Types
enum class FollowUpType { CALL, EMAIL, MEETING, WHATSAPP, VISIT, OTHER }

// Product Types
enum class ProductType { PHYSICAL, DIGITAL, SERVICE, SOFTWARE }

// Service Types
enum class ServiceType { ONLINE, OFFLINE, HYBRID }

// Custom Field Types
enum class CustomFieldType { TEXT, NUMBER, DATE, TIME, DATETIME, DROPDOWN, CHECKBOX, PHONE, EMAIL, URL, TEXTAREA, CURRENCY }

// Note Types
enum class NoteType { GENERAL, CALL_LOG, MEETING, EMAIL, TASK, IMPORTANT, FOLLOW_UP, DEAL, FEEDBACK, INTERNAL }

// Note Priority
enum class NotePriority { LOW, NORMAL, HIGH, URGENT }

// Payment Types
enum class PaymentType { RECEIVED, GIVEN }

// Invoice Status
enum class InvoiceStatus { DRAFT, SENT, PAID, CANCELLED }

// Timeline Event Types
enum class TimelineEventType { LEAD_CREATED, STATUS_CHANGED, NOTE_ADDED, FOLLOWUP_SCHEDULED, FOLLOWUP_COMPLETED, CALL_MADE, MESSAGE_SENT, EMAIL_SENT, MEETING_SCHEDULED, PRODUCT_ASSIGNED, IMAGE_ADDED, CUSTOM_EVENT }

// Keyword Match Types
enum class KeywordMatchType { EXACT, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX }
```

## 🔥 Firestore Structure

```
users/
└── {user_email_sanitized}/
    └── chatspromo_crm/
        ├── meta/
        │   └── sync_info
        │       ├── lastBackupAt: Long
        │       ├── lastRestoreAt: Long
        │       ├── appVersion: String
        │       ├── totalLeads: Int
        │       ├── totalProducts: Int
        │       └── deviceId: String
        │
        ├── leads/
        │   └── {lead_id}/
        │       ├── id: String
        │       ├── name: String
        │       ├── phoneNumber: String
        │       ├── email: String
        │       ├── countryCode: String
        │       ├── countryIso: String
        │       ├── alternatePhone: String
        │       ├── status: String (enum name)
        │       ├── source: String
        │       ├── lastMessage: String
        │       ├── timestamp: Long
        │       ├── category: String
        │       ├── notes: String
        │       ├── priority: String (enum name)
        │       ├── tags: String (JSON array)
        │       ├── product: String
        │       ├── leadScore: Int
        │       ├── nextFollowUpDate: Long?
        │       ├── isFollowUpCompleted: Boolean
        │       └── lastModifiedAt: Long
        │
        ├── follow_ups/
        │   └── {followup_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── title: String
        │       ├── description: String
        │       ├── scheduledDate: Long
        │       ├── scheduledTime: String
        │       ├── type: String (enum name)
        │       ├── isCompleted: Boolean
        │       ├── completedDate: Long?
        │       ├── notes: String
        │       ├── reminderMinutes: Int
        │       └── lastModifiedAt: Long
        │
        ├── products/
        │   └── {product_id}/
        │       ├── id: String
        │       ├── name: String
        │       ├── type: String (enum name)
        │       ├── category: String
        │       ├── subcategory: String
        │       ├── mrp: String
        │       ├── sellingPrice: String
        │       ├── description: String
        │       ├── color: String
        │       ├── size: String
        │       ├── height: String
        │       ├── width: String
        │       ├── weight: String
        │       ├── downloadLink: String
        │       ├── licenseType: String
        │       ├── version: String
        │       ├── serviceType: String? (enum name)
        │       ├── duration: String
        │       ├── deliveryTime: String
        │       └── lastModifiedAt: Long
        │
        ├── custom_field_definitions/
        │   └── {field_id}/
        │       ├── id: String
        │       ├── fieldName: String
        │       ├── fieldType: String (enum name)
        │       ├── isRequired: Boolean
        │       ├── defaultValue: String
        │       ├── options: String (JSON array)
        │       ├── displayOrder: Int
        │       ├── isActive: Boolean
        │       ├── createdAt: Long
        │       └── lastModifiedAt: Long
        │
        ├── custom_field_values/
        │   └── {leadId_fieldId}/
        │       ├── leadId: String
        │       ├── fieldId: String
        │       ├── fieldValue: String
        │       ├── updatedAt: Long
        │       └── lastModifiedAt: Long
        │
        ├── notes/
        │   └── {note_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── title: String
        │       ├── content: String
        │       ├── noteType: String (enum name)
        │       ├── priority: String (enum name)
        │       ├── isPinned: Boolean
        │       ├── createdAt: Long
        │       ├── updatedAt: Long
        │       ├── createdBy: String
        │       ├── attachments: String (JSON array)
        │       ├── tags: String (JSON array)
        │       ├── isArchived: Boolean
        │       ├── parentNoteId: String?
        │       └── lastModifiedAt: Long
        │
        ├── timeline/
        │   └── {timeline_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── eventType: String (enum name)
        │       ├── title: String
        │       ├── description: String
        │       ├── timestamp: Long
        │       ├── imageUri: String?
        │       ├── metadata: String (JSON)
        │       ├── iconType: String
        │       └── lastModifiedAt: Long
        │
        ├── chat_messages/
        │   └── {message_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── senderName: String
        │       ├── senderPhone: String
        │       ├── messageText: String
        │       ├── timestamp: Long
        │       ├── isIncoming: Boolean
        │       ├── isAutoReply: Boolean
        │       ├── matchedKeyword: String?
        │       ├── replyType: String
        │       ├── packageName: String
        │       ├── isRead: Boolean
        │       └── lastModifiedAt: Long
        │
        ├── payments/
        │   └── {payment_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── amount: Double
        │       ├── paymentType: String (enum name)
        │       ├── description: String
        │       ├── timestamp: Long
        │       ├── createdAt: Long
        │       └── lastModifiedAt: Long
        │
        ├── invoices/
        │   └── {invoice_id}/
        │       ├── id: String
        │       ├── leadId: String
        │       ├── invoiceNumber: String
        │       ├── amount: Double
        │       ├── tax: Double
        │       ├── totalAmount: Double
        │       ├── status: String (enum name)
        │       ├── addressTo: String
        │       ├── addressFrom: String
        │       ├── comments: String
        │       ├── items: String (JSON array)
        │       ├── timestamp: Long
        │       ├── dueDate: Long?
        │       ├── createdAt: Long
        │       └── lastModifiedAt: Long
        │
        └── settings/
            └── auto_add/
                ├── settings/
                │   └── {settings document}
                └── keyword_rules/
                    └── {rule_id}/
                        └── {rule document}
```

## 🔄 Sync Logic

### Backup Flow

```
1. User clicks "Backup" button
2. Check if user is logged in (Firebase Auth)
3. Get user email → sanitize for document ID
4. Collect all data from Room database
5. Upload in batches (max 450 per batch for Firestore limit)
6. Update meta/sync_info with timestamp
7. Show success message with stats
```

### Restore Flow

```
1. User clicks "Restore" button
2. Check if user is logged in
3. Get user email → sanitize for document ID
4. Check if backup exists in Firestore
5. Download all collections
6. Compare timestamps (cloud vs local)
7. Merge strategy:
   - If cloud newer → replace local
   - If local newer → keep local (or ask user)
   - If not exists locally → insert
8. Update local restore timestamp
9. Show success message with stats
```

### Real-time Sync (Optional)

```kotlin
// Listen to Firestore changes
firestore.collection("users/$userId/chatspromo_crm/leads")
    .addSnapshotListener { snapshots, error ->
        // Handle real-time updates
    }
```

## 📁 Files to Create

```
leadmanager/sync/
├── CHATSPROMO_CRM_README.md          # This file
├── CRMSyncManager.kt                  # Main sync manager
├── CRMBackupService.kt                # Backup logic
├── CRMRestoreService.kt               # Restore logic
├── CRMSyncActivity.kt                 # UI for sync
├── models/
│   ├── BackupData.kt                  # Data classes for backup
│   └── SyncStatus.kt                  # Sync status model
└── utils/
    └── FirestoreConverter.kt          # Entity ↔ Firestore conversion
```

## 🛠️ Implementation Steps

### Step 1: Create Data Models for Backup

```kotlin
data class CRMBackupData(
    val leads: List<LeadBackup>,
    val followUps: List<FollowUpBackup>,
    val products: List<ProductBackup>,
    val customFieldDefinitions: List<CustomFieldDefinitionBackup>,
    val customFieldValues: List<CustomFieldValueBackup>,
    val notes: List<NoteBackup>,
    val timeline: List<TimelineBackup>,
    val chatMessages: List<ChatMessageBackup>,
    val payments: List<PaymentBackup>,
    val invoices: List<InvoiceBackup>,
    val autoAddSettings: AutoAddSettingsBackup?,
    val keywordRules: List<KeywordRuleBackup>,
    val backupTimestamp: Long,
    val appVersion: String
)
```

### Step 2: Create CRMSyncManager

```kotlin
class CRMSyncManager(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val db = LeadManagerDatabase.getDatabase(context)
    
    // Backup all CRM data
    suspend fun backupAll(onProgress: (String, Int) -> Unit): Result<BackupStats>
    
    // Restore all CRM data
    suspend fun restoreAll(onProgress: (String, Int) -> Unit): Result<RestoreStats>
    
    // Get sync status
    fun getSyncStatus(): CRMSyncStatus
    
    // Enable real-time sync
    fun enableRealtimeSync()
    
    // Disable real-time sync
    fun disableRealtimeSync()
}
```

### Step 3: Implement Backup Logic

```kotlin
suspend fun backupAll(onProgress: (String, Int) -> Unit): Result<BackupStats> {
    val userId = getUserId() ?: return Result.failure(Exception("Not logged in"))
    val basePath = "users/$userId/chatspromo_crm"
    
    var totalItems = 0
    var backedUp = 0
    
    // 1. Backup Leads
    val leads = db.leadDao().getAllLeadsList()
    totalItems += leads.size
    onProgress("Backing up ${leads.size} leads...", 10)
    backupCollection(basePath, "leads", leads.map { it.toBackupMap() })
    backedUp += leads.size
    
    // 2. Backup Follow-ups
    val followUps = db.followUpDao().getAllFollowUpsList()
    // ... continue for all entities
    
    // Update meta
    updateSyncMeta(userId, totalItems)
    
    return Result.success(BackupStats(totalItems, backedUp))
}
```

### Step 4: Implement Restore Logic

```kotlin
suspend fun restoreAll(onProgress: (String, Int) -> Unit): Result<RestoreStats> {
    val userId = getUserId() ?: return Result.failure(Exception("Not logged in"))
    val basePath = "users/$userId/chatspromo_crm"
    
    var inserted = 0
    var updated = 0
    var skipped = 0
    
    // 1. Restore Leads first (parent entity)
    onProgress("Restoring leads...", 10)
    val cloudLeads = fetchCollection(basePath, "leads")
    for (cloudLead in cloudLeads) {
        val localLead = db.leadDao().getLeadById(cloudLead.id)
        when {
            localLead == null -> {
                db.leadDao().insertLead(cloudLead.toEntity())
                inserted++
            }
            cloudLead.lastModifiedAt > localLead.timestamp -> {
                db.leadDao().updateLead(cloudLead.toEntity())
                updated++
            }
            else -> skipped++
        }
    }
    
    // 2. Restore child entities (follow-ups, notes, etc.)
    // ... continue for all entities
    
    return Result.success(RestoreStats(inserted, updated, skipped))
}
```

## 🎨 UI Design

### Sync Screen Layout

```
┌─────────────────────────────────────┐
│  ← Chatspromo CRM Sync              │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐   │
│  │  👤 user@email.com          │   │
│  │  Last Backup: 2 hours ago   │   │
│  │  Last Restore: Yesterday    │   │
│  └─────────────────────────────┘   │
│                                     │
│  📊 Local Data Summary              │
│  ├─ Leads: 150                      │
│  ├─ Products: 25                    │
│  ├─ Follow-ups: 45                  │
│  ├─ Notes: 89                       │
│  └─ Custom Fields: 12               │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  ☁️ BACKUP TO CLOUD         │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  📥 RESTORE FROM CLOUD      │   │
│  └─────────────────────────────┘   │
│                                     │
│  ⚙️ Settings                        │
│  ├─ [ ] Auto backup on exit         │
│  ├─ [ ] Real-time sync              │
│  └─ [ ] Backup chat messages        │
│                                     │
└─────────────────────────────────────┘
```

## ⚠️ Important Considerations

### 1. Data Integrity
- Always backup leads first (parent entity)
- Restore in correct order: Leads → Products → Follow-ups → Notes → etc.
- Handle foreign key constraints properly

### 2. Conflict Resolution
- Use `lastModifiedAt` timestamp for conflict detection
- Cloud wins by default (user's backup is trusted)
- Option to show conflict dialog for manual resolution

### 3. Performance
- Use Firestore batch writes (max 500 operations)
- Chunk large collections into batches of 450
- Show progress indicator during sync

### 4. Error Handling
- Retry failed operations
- Log errors for debugging
- Show user-friendly error messages

### 5. Security
- User can only access their own data
- Email sanitization for document IDs
- Firestore security rules required

## 🔒 Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/chatspromo_crm/{document=**} {
      allow read, write: if request.auth != null 
        && request.auth.token.email.replace('.', '_').replace('@', '_at_') == userId;
    }
  }
}
```

## 📱 Integration Points

### 1. LeadManagerActivity
- Add "Sync" button in toolbar/menu
- Navigate to CRMSyncActivity

### 2. Auto Backup
- Trigger backup on app exit (optional)
- Background sync with WorkManager

### 3. Settings Screen
- Add sync settings section
- Configure auto-backup preferences

## 🚀 Next Steps

1. Create `CRMSyncManager.kt` with backup/restore logic
2. Create `CRMSyncActivity.kt` with UI
3. Add sync button to LeadManagerActivity
4. Test with sample data
5. Add real-time sync (optional)
6. Add auto-backup feature (optional)

---

**Author:** Kiro AI Assistant  
**Date:** December 2024  
**Version:** 1.0
