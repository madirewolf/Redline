package com.redline.app.community.local

/** The rows written the first time the Community tab opens. */
data class CommunitySeed(
    val gyms: List<CommunityGymEntity>,
    val members: List<CommunityMemberEntity>,
    val lifts: List<CommunityLiftEntity>,
    val posts: List<CommunityPostEntity>,
    val conversations: List<CommunityConversationEntity>,
    val messages: List<CommunityMessageEntity>
)

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Conversation ids for direct messages: one thread per member. */
internal fun directThreadId(memberId: String) = "dm-$memberId"

/**
 * Made-up people, gyms and posts so the tab has something in it before there
 * are real accounts. Everyone here is fictional. Times are relative to [now],
 * so the feed reads as recent on first open.
 */
internal fun sampleCommunity(now: Long): CommunitySeed {
    val gyms = listOf(
        gym("ironworks", "Ironworks Barbell Club", "Riverside", 0.8f, 72, "Powerlifting, Chalk OK, Open 24/7", "Open 24 hours", 214),
        gym("northgate", "Northgate Fitness", "Northgate", 1.4f, 35, "Cardio, Classes, Pool", "5 AM – 11 PM", 388),
        gym("foundry", "The Foundry Strength Lab", "Old Mill", 2.1f, 55, "Olympic lifting, Platforms, Coaching", "6 AM – 10 PM", 126),
        gym("eastside", "Eastside Community Gym", "Eastside", 2.9f, 20, "Beginner friendly, Free weights, Budget", "6 AM – 9 PM", 97),
        gym("summit", "Summit Climb & Lift", "Hillcrest", 3.6f, 48, "Climbing, Yoga, Free weights", "7 AM – 10 PM", 152),
        gym("central", "Central Athletic Club", "Downtown", 4.2f, 88, "Sauna, Classes, Turf", "Open 24 hours", 503)
    )

    val members = listOf(
        member("maya", "Maya", "maya.lifts", "Powerlifting, mostly squats. Coffee before everything.", "ironworks", true, 4, 15, 412_000, 23),
        member("theo", "Theo", "theo_trains", "Lifting plus half marathons. Hybrid everything.", "northgate", true, 3, 11, 198_500, 9),
        member("priya", "Priya", "priyapulls", "Deadlifts and pull-ups. Slowly learning to love leg day.", "foundry", true, 5, 18, 286_000, 14),
        member("jonah", "Jonah", "jonah.benches", "Bench press enjoyer. Will spot you.", "ironworks", true, 2, 9, 241_000, 4),
        member("sofia", "Sofia", "sofi.strength", "Thirty-plus weeks without missing one.", "central", true, 4, 14, 233_000, 31),
        member("marcus", "Marcus", "marcus_moves", "Early mornings, heavy pulls.", "ironworks", false, 3, 12, 310_000, 6),
        member("lena", "Lena", "lena.lifts", "First real program. Here to learn.", "summit", false, 2, 8, 96_000, 3),
        member("dev", "Dev", "dev.pulls", "Coach at the Foundry. Ask me about deadlifts.", "foundry", false, 6, 21, 455_000, 40),
        member("rosa", "Rosa", "rosa.reps", "New to lifting, loving it so far.", "eastside", false, 1, 5, 54_000, 1)
    )

    val lifts = listOf(
        lift("maya", "back_squat", "Back Squat", 315f),
        lift("maya", "bench_press", "Bench Press", 165f),
        lift("maya", "deadlift", "Deadlift", 345f),
        lift("maya", "overhead_press", "Overhead Press", 105f),
        lift("theo", "bench_press", "Bench Press", 205f),
        lift("theo", "back_squat", "Back Squat", 245f),
        lift("theo", "deadlift", "Deadlift", 315f),
        lift("theo", "overhead_press", "Overhead Press", 125f),
        lift("priya", "deadlift", "Deadlift", 285f),
        lift("priya", "back_squat", "Back Squat", 205f),
        lift("priya", "bench_press", "Bench Press", 115f),
        lift("priya", "barbell_row", "Barbell Row", 135f),
        lift("jonah", "bench_press", "Bench Press", 275f),
        lift("jonah", "incline_bench_press", "Incline Bench Press", 225f),
        lift("jonah", "back_squat", "Back Squat", 275f),
        lift("jonah", "deadlift", "Deadlift", 365f),
        lift("sofia", "back_squat", "Back Squat", 225f),
        lift("sofia", "deadlift", "Deadlift", 275f),
        lift("sofia", "hip_thrust", "Hip Thrust", 315f),
        lift("sofia", "bench_press", "Bench Press", 135f),
        lift("marcus", "bench_press", "Bench Press", 245f),
        lift("marcus", "back_squat", "Back Squat", 335f),
        lift("marcus", "deadlift", "Deadlift", 425f),
        lift("lena", "bench_press", "Bench Press", 95f),
        lift("lena", "back_squat", "Back Squat", 155f),
        lift("lena", "deadlift", "Deadlift", 185f),
        lift("dev", "deadlift", "Deadlift", 495f),
        lift("dev", "back_squat", "Back Squat", 405f),
        lift("dev", "bench_press", "Bench Press", 285f),
        lift("dev", "overhead_press", "Overhead Press", 175f),
        lift("rosa", "back_squat", "Back Squat", 135f),
        lift("rosa", "bench_press", "Bench Press", 85f),
        lift("rosa", "deadlift", "Deadlift", 155f)
    )

    val posts = listOf(
        post("maya", "Squat PR! 315 for a single, finally. Three months stuck at 295.", "Leg day · 16 sets · 14.2k lbs", "ironworks", now - 25 * MINUTE, 24, 7),
        post("theo", "Easy 8k run this morning, upper body tonight. Anyone else running a hybrid block?", null, "northgate", now - 2 * HOUR, 9, 4),
        post("priya", "Pull-ups went from 3 to 8 strict in six weeks. Slow negatives and patience.", "Pull day · 18 sets · 9,820 lbs", "foundry", now - 4 * HOUR, 31, 6),
        post("dev", "Deload week. Joints happy, bar speed back. Heavy pulls again on Monday.", null, "foundry", now - 6 * HOUR, 12, 2),
        post("jonah", "Bench is moving again after adding a pause on the chest. 275 for a double.", "Push day · 14 sets · 11.5k lbs", "ironworks", now - 9 * HOUR, 18, 5),
        post("sofia", "31 weeks in a row without missing one. Showing up beats going hard once.", null, "central", now - 20 * HOUR, 46, 11),
        post("lena", "First week on a real program. The squat rack is a lot less scary than I thought.", "Full body · 12 sets · 6,240 lbs", "summit", now - 1 * DAY - 5 * HOUR, 38, 14),
        post("marcus", "Anyone at Ironworks want to share a 6 AM slot? The 5:30 crowd is wild.", null, "ironworks", now - 2 * DAY, 7, 9),
        post("rosa", "Joined Eastside last week. Staff are friendly and nobody hogs the racks. Good for beginners.", null, "eastside", now - 3 * DAY, 15, 3),
        post("theo", "Half marathon done in 1:52, and still trained legs two days later.", "Legs · 12 sets · 8,100 lbs", "northgate", now - 4 * DAY, 27, 8)
    )

    // Maya's thread has two unread, Priya's one, Theo's none.
    val mayaRead = now - DAY - 3 * HOUR
    val theoRead = now - 3 * DAY + 2 * HOUR
    val priyaRead = now - DAY
    val conversations = listOf(
        CommunityConversationEntity(directThreadId("maya"), "maya", createdAt = now - 2 * DAY, lastReadAt = mayaRead),
        CommunityConversationEntity(directThreadId("theo"), "theo", createdAt = now - 4 * DAY, lastReadAt = theoRead),
        CommunityConversationEntity(directThreadId("priya"), "priya", createdAt = now - 2 * DAY, lastReadAt = priyaRead)
    )
    val messages = listOf(
        message("maya", "maya", "Saw you logged legs yesterday. How did squats feel?", mayaRead - 20 * MINUTE),
        message("maya", ME, "Heavy but good. Going for a PR next week.", mayaRead),
        message("maya", "maya", "Let's go! I finally hit 315 today.", now - 40 * MINUTE),
        message("maya", "maya", "Want to train together Saturday at Ironworks?", now - 38 * MINUTE),
        message("theo", "theo", "What program are you running right now?", theoRead - 30 * MINUTE),
        message("theo", ME, "Upper/lower, four days a week.", theoRead - 10 * MINUTE),
        message("theo", "theo", "Nice. Might borrow that for my off-season.", theoRead),
        message("priya", "priya", "Thanks for the tip on negatives. Pull-ups went from 3 to 8!", now - 5 * HOUR)
    )

    return CommunitySeed(gyms, members, lifts, posts, conversations, messages)
}

private fun gym(
    id: String, name: String, area: String, miles: Float, crowd: Int, tags: String, hours: String, members: Int
) = CommunityGymEntity(id, name, area, miles, crowd, tags, hours, members)

private fun member(
    id: String, name: String, handle: String, bio: String, gymId: String, friend: Boolean,
    week: Int, month: Int, volume: Long, streak: Int
) = CommunityMemberEntity(id, name, handle, bio, gymId, friend, week, month, volume, streak)

private fun lift(memberId: String, key: String, name: String, lbs: Float) =
    CommunityLiftEntity(memberId, key, name, lbs)

private fun post(
    authorId: String, text: String, workout: String?, gymId: String, at: Long, likes: Int, comments: Int
) = CommunityPostEntity(
    authorId = authorId, text = text, workoutSummary = workout, gymId = gymId,
    createdAt = at, likeCount = likes, commentCount = comments, likedByMe = false
)

private fun message(memberId: String, senderId: String, text: String, at: Long) =
    CommunityMessageEntity(conversationId = directThreadId(memberId), senderId = senderId, text = text, sentAt = at)
