class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModel()


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainTheme {
                viewModel.navController = rememberNavController()
                RequestNotificationPermission()
                NavigationRoot(viewModel = viewModel)
            }
        }
    }
}
internal class MainActivityViewModel(
    private val getLatestReleaseUseCase: GetLatestReleaseUseCase,
    private val rebootingRemindersUseCase: RebootingRemindersUseCase,
    context: Context,
    private val settingsManager: SettingsManager,
    private val refresher: Refresher,
) : ViewModel(), BottomBarNavigator {

    var release: Release? = null
        private set

    val versionCode = context.getVersionCode()
    val versionName = context.getVersionName()

    suspend fun checkUpdates(): Release? {
        val localRelease = getLatestReleaseUseCase.invoke()
        return if (localRelease != null && versionName != null &&
            localRelease.tagName.substringAfter("v") != versionName
        ) {
            saveSettingsToSharedPreferences {
                it.copy(releaseBody = localRelease.body)
            }
            release = localRelease
            release
        } else null
    }

    fun update() {
        release?.let {
            viewModelScope.launch(Dispatchers.IO) {
                refresher.refresh(viewModelScope, it.apkUrl, RefresherInfo.APK_FILE_NAME)
            }
        }
    }

    val isUpdateInProcessFlow: SharedFlow<Boolean> = refresher.progressFlow.map {
        it !is DownloadStatus.Error && it !is DownloadStatus.Success
    }.shareIn(viewModelScope, SharingStarted.Lazily)

    val percentageFlow: SharedFlow<Float> = refresher.progressFlow.map {
        when (it) {
            is DownloadStatus.InProgress -> it.percentage
            DownloadStatus.Success -> 100f
            else -> 0f
        }
    }.shareIn(viewModelScope, SharingStarted.Lazily)

    val settings = settingsManager.settingsFlow

    fun saveSettingsToSharedPreferences(saving: (Settings) -> Settings) {
        settingsManager.save(saving)
    }

    private fun registerGeneralExceptionCallback(context: Context) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            context.copyTextToClipBoard(
                label = "Error",
                text = "Contact us about this problem: ${Link.EMAIL}\n\n Exception in ${thread.name} thread\n${throwable.stackTraceToString()}"
            )
            Log.e("GeneralException", "Exception in thread ${thread.name}", throwable)
            exitProcess(0)
        }
    }

    init {
        registerGeneralExceptionCallback(context = context)
    }

    val startScreen = Screen.Main

    override var navController: NavHostController? = null

    private val _currentScreen: MutableStateFlow<Screen> = MutableStateFlow(startScreen)
    override val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private fun emitCurrentScreen() {
        viewModelScope.launch(Dispatchers.Main) {
            navController?.currentBackStackEntryFlow?.collect { backStackEntry ->
                val destination = backStackEntry.destination
                when {
                    destination.hasRoute(Screen.Main::class) -> _currentScreen.emit(Screen.Main)
                    destination.hasRoute(Screen.Settings::class) -> _currentScreen.emit(Screen.Settings)
                }
            }
        }
    }

    override fun back() {
        navController?.popBackStack()
        emitCurrentScreen()
    }

    override fun navigateTo(screen: Screen) {
        if (screen != Screen.Main)
            navController?.navigate(screen)
        else navController?.popBackStack()
        emitCurrentScreen()
    }
}
@Composable
internal fun MainScreen(bottomBarNavigator: BottomBarNavigator) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val viewModel: MainScreenViewModel = koinViewModel()
    val context = LocalContext.current

    val settings by viewModel.settingsFlow.collectAsState()
    val defaultColor = Theme.colors.mainColor

    val dark = isSystemInDarkTheme()
    val mainColor by remember {
        mutableStateOf(
            settings.getMainColorForThisTheme(isDark = dark) ?: defaultColor
        )
    }

    var isFrequencyInChanging by remember { mutableStateOf(false) }
    val cor = rememberCoroutineScope()
    var localTime by remember { mutableStateOf(LocalTime.now()) }
    var localDate by remember { mutableStateOf(LocalDate.now()) }

    val weekOfYear by remember {
        derivedStateOf {
            localDate.getFrequencyByLocalDate()
                .changeFrequencyIfDefinedInSettings(settings = settings)
        }
    }

    val lessons by viewModel.lessonsFlow.collectAsState(listOf())
    val cardsWithDateState = rememberLazyListState()

    fun chooseTypeOfDefinitionFrequencyDependsOn(selectedFrequency: Frequency?) {
        viewModel.saveSettingsToSharedPreferences {
            it.copy(isSelectedFrequencyCorrespondsToTheWeekNumbers = selectedFrequency?.let { localDate.getFrequencyByLocalDate() == it })
        }
        isFrequencyInChanging = false
    }

    val pagerState = rememberPagerState(pageCount = { viewModel.pageNumber }, initialPage = 0)

    BackHandler {
        when {
            pagerState.currentPage == 0 -> {
                bottomBarNavigator.back()
                (context as Activity).finish()
            }

            else -> {
                cor.launch(Dispatchers.Main) {
                    pagerState.animateScrollToPage(0)
                }
            }
        }
    }

    LaunchedEffect(key1 = null) {
        withContext(Dispatchers.Main) {
            while (true) {
                localTime = LocalTime.now()
                delay(timeMillis = 1000L)
            }
        }
    }

    LaunchedEffect(key1 = pagerState.currentPage) {
        localDate = viewModel.todayDate.plusDays(pagerState.currentPage.toLong())
        cardsWithDateState.animateScrollToItem(if (pagerState.currentPage > 0) pagerState.currentPage - 1 else pagerState.currentPage)
    }

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            Lifecycle.State.DESTROYED -> {}
            Lifecycle.State.INITIALIZED -> {}
            Lifecycle.State.CREATED -> {}
            Lifecycle.State.STARTED -> {}
            Lifecycle.State.RESUMED -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isFrequencyInChanging = true }) {
                    Text(
                        text = stringResource(id = weekOfYear.resourceName),
                        fontSize = FontSize.big22,
                        color = Theme.colors.oppositeTheme,
                    )
                    Icon(
                        painter = painterResource(id = if (isFrequencyInChanging) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                        contentDescription = stringResource(R.string.icon_fold_or_unfold_list_with_frequency),
                        tint = Theme.colors.oppositeTheme,
                    )
                }

                DropdownMenu(
                    modifier = Modifier
                        .background(Theme.colors.singleTheme)
                        .border(BorderStroke(width = 2.dp, color = Theme.colors.oppositeTheme)),
                    expanded = isFrequencyInChanging,
                    onDismissRequest = { isFrequencyInChanging = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row {
                                TextForThisTheme(
                                    text = stringResource(id = Frequency.Numerator.resourceName),
                                    fontSize = FontSize.medium19
                                )
                                if (settings.isSelectedFrequencyCorrespondsToTheWeekNumbers != null
                                    && weekOfYear == Frequency.Numerator
                                )
                                    Icon(
                                        painterResource(id = R.drawable.done),
                                        contentDescription = stringResource(R.string.this_is_selected_or_not),
                                        tint = Theme.colors.oppositeTheme
                                    )
                            }
                        },
                        onClick = { chooseTypeOfDefinitionFrequencyDependsOn(selectedFrequency = Frequency.Numerator) })

                    Spacer(
                        modifier = Modifier
                            .height(2.dp)
                            .padding(horizontal = 15.dp)
                            .background(color = Theme.colors.oppositeTheme)
                    )

                    DropdownMenuItem(
                        text = {
                            Row {
                                TextForThisTheme(
                                    text = stringResource(id = Frequency.Denominator.resourceName),
                                    fontSize = FontSize.medium19
                                )
                                if (settings.isSelectedFrequencyCorrespondsToTheWeekNumbers != null
                                    && weekOfYear == Frequency.Denominator
                                )
                                    Icon(
                                        painterResource(id = R.drawable.done),
                                        contentDescription = stringResource(R.string.this_is_selected_or_not),
                                        tint = Theme.colors.oppositeTheme
                                    )
                            }
                        },
                        onClick = { chooseTypeOfDefinitionFrequencyDependsOn(selectedFrequency = Frequency.Denominator) })

                    Spacer(
                        modifier = Modifier
                            .height(2.dp)
                            .padding(horizontal = 15.dp)
                            .background(color = Theme.colors.oppositeTheme)
                    )

                    DropdownMenuItem(
                        text = {
                            Row {
                                TextForThisTheme(
                                    text = stringResource(id = R.string.auto),
                                    fontSize = FontSize.medium19
                                )
                                if (settings.isSelectedFrequencyCorrespondsToTheWeekNumbers == null)
                                    Icon(
                                        painterResource(id = R.drawable.done),
                                        contentDescription = stringResource(R.string.this_is_selected_or_not),
                                        tint = Theme.colors.oppositeTheme
                                    )
                            }
                        },
                        onClick = { chooseTypeOfDefinitionFrequencyDependsOn(selectedFrequency = null) })
                }
            }
        }

        LazyRow(
            state = cardsWithDateState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items(count = viewModel.pageNumber) { index ->
                val day by remember { mutableStateOf(viewModel.todayDate.plusDays(index.toLong())) }
                Column {
                    Card(
                        modifier = Modifier
                            .fillParentMaxWidth(1 / 3f)
                            .padding(horizontal = 5.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (day == viewModel.todayDate) mainColor else Theme.colors.buttonColor,
                            contentColor = (if (day == viewModel.todayDate) mainColor else Theme.colors.buttonColor).suitableColor(),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    ) {
                        Text(
                            text = day.getDateStringWithWeekOfDay(context = context),
                            fontSize = FontSize.small17,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cor.launch(Dispatchers.Main) {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 5.dp, horizontal = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (day == localDate)
                        Card(
                            modifier = Modifier
                                .fillParentMaxWidth(1 / 3f)
                                .padding(top = 2.dp)
                                .padding(horizontal = 18.dp)
                                .height(2.dp),
                            colors = CardDefaults.cardColors(containerColor = Theme.colors.oppositeTheme)
                        ) {}
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(10f),
        ) { page ->
            val dateOfThisLesson = viewModel.todayDate.plusDays(page.toLong())
            val weekOfYearOfThisDay = dateOfThisLesson.getFrequencyByLocalDate()
                .changeFrequencyIfDefinedInSettings(settings = settings)

            val lessonsOfThisDay = lessons.filter {
                it.dayOfWeek == dateOfThisLesson.dayOfWeek &&
                        (it.frequency == null || it.frequency == weekOfYearOfThisDay) &&
                        if (settings.role == Role.Student) (it.subGroup == settings.subgroup || settings.subgroup == null || it.subGroup == null)
                        else it.teacher == settings.teacherName
            }.sorted()

            val lessonsInOppositeNumAndDenDay = lessons.filter {
                it.dayOfWeek == dateOfThisLesson.dayOfWeek &&
                        it.frequency == weekOfYearOfThisDay.getOpposite() &&
                        if (settings.role == Role.Student) (it.subGroup == settings.subgroup || settings.subgroup == null || it.subGroup == null)
                        else it.teacher == settings.teacherName
            }.sorted()

            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                ) {
                    if (lessonsOfThisDay.isNotEmpty()) {
                        viewModel.nowIsLesson = false

                        lessonsOfThisDay.forEach { lesson ->
                            if (lesson.nowIsLesson(localTime) && viewModel.todayDate == dateOfThisLesson) {
                                viewModel.nowIsLesson = true

                                lesson.StringForSchedule(
                                    colorBack = mainColor,
                                    dateOfThisLesson = dateOfThisLesson,
                                    viewModel = viewModel,
                                    isNoteAvailable = settings.notesAboutLesson,
                                    isNotificationsAvailable = settings.notificationsAboutLesson,
                                )
                            } else if (viewModel.todayDate == dateOfThisLesson && lessonsOfThisDay.any { it.startTime > localTime } && lesson == lessonsOfThisDay.filter { it.startTime > localTime }[0] && !viewModel.nowIsLesson) {
                                CardOfNextLesson(colorOfCard = mainColor) {
                                    lesson.StringForSchedule(
                                        paddingValues = PaddingValues(),
                                        colorBack = Theme.colors.buttonColor,
                                        dateOfThisLesson = dateOfThisLesson,
                                        viewModel = viewModel,
                                        isNoteAvailable = settings.notesAboutLesson,
                                        isNotificationsAvailable = settings.notificationsAboutLesson,
                                    )
                                }
                            } else lesson.StringForSchedule(
                                colorBack = Theme.colors.buttonColor,
                                dateOfThisLesson = dateOfThisLesson,
                                viewModel = viewModel,
                                isNoteAvailable = settings.notesAboutLesson,
                                isNotificationsAvailable = settings.notificationsAboutLesson,
                            )
                        }
                    } else WeekDay(
                        isCatShowed = settings.weekendCat,
                        viewModel = viewModel,
                        context = context,
                        modifier = Modifier.let {
                            var modifier =
                                Modifier.padding(vertical = 100.dp); if (lessonsInOppositeNumAndDenDay.isEmpty()) modifier =
                            Modifier.weight(1f); modifier
                        })

                    if (lessonsInOppositeNumAndDenDay.isNotEmpty()) {
                        TextForThisTheme(
                            text = "${stringResource(id = R.string.other_lessons_in_this_day)} ${
                                stringResource(
                                    id = weekOfYearOfThisDay.getOpposite().resourceName
                                )
                            }",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = FontSize.big22,
                        )

                        lessonsInOppositeNumAndDenDay.forEach { lesson ->
                            lesson.StringForSchedule(
                                colorBack = Theme.colors.buttonColor,
                                dateOfThisLesson = null,
                                viewModel = viewModel,
                                isNoteAvailable = settings.notesAboutLesson,
                                isNotificationsAvailable = settings.notificationsAboutLesson,
                            )
                        }
                    }
                }


            }
        }
    }
}

@Composable
internal fun SettingsScreen(bottomBarNavigator: BottomBarNavigator) {
    val viewModel: SettingsScreenViewModel = koinViewModel()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val subgroupList by viewModel.subgroupFlow.collectAsState(listOf())
    val groupList by viewModel.groupFlow.collectAsState(listOf())
    val teachersList by viewModel.teachersFlow.collectAsState(listOf())
    val settings by viewModel.settings.collectAsState()

    var colorIsEditable by remember { mutableStateOf(false) }
    var isFeaturesEditable by remember { mutableStateOf(false) }
    var isGroupChanging by remember { mutableStateOf(false) }
    var isSubGroupChanging by remember { mutableStateOf(false) }
    var isTeacherChanging by remember { mutableStateOf(false) }
    var catsOnUIIsChanging by remember { mutableStateOf(false) }
    var isRoleChanging by remember { mutableStateOf(false) }
    val networkState by viewModel.gSheetsServiceRequestStatusFlow.collectAsState()

    BackHandler {
        bottomBarNavigator.back()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Icon(
                    painter = painterResource(
                        id = getIconByRequestStatus(
                            networkState = networkState
                        )
                    ),
                    contentDescription = stringResource(R.string.icon_data_updating_state),
                    tint = Theme.colors.oppositeTheme
                )
                Spacer(modifier = Modifier.width(15.dp))
                TextForThisTheme(
                    text = stringResource(R.string.settings),
                    fontSize = FontSize.big22
                )
            }
        }

        if (colorIsEditable) {
            ColorPickerDialog(
                context = context,
                firstColor = settings.getMainColorForThisTheme(isDark = dark)
                    ?: Theme.colors.mainColor,
                onDismissRequest = { colorIsEditable = false }
            ) {
                viewModel.saveSettingsToSharedPreferences { settings ->
                    if (dark) settings.copy(
                        darkThemeColor = it
                    ) else settings.copy(lightThemeColor = it)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                TextForThisTheme(
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = FontSize.big22,
                    text = stringResource(R.string.general)
                )
                if (settings.catInSettings) {
                    GifPlayer(
                        size = 80.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { viewModel.meow() },
                        imageUri = Uri.parse(AssetsInfo.FUNNY_SETTINGS_CAT)
                    )
                }
            }

            CardOfSettings(
                text = stringResource(R.string.role),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.role),
                        contentDescription = stringResource(R.string.role),
                        tint = it.suitableColor()
                    )
                },
                onClick = {
                    isRoleChanging = !isRoleChanging
                },
                additionalContentIsVisible = isRoleChanging,
                additionalContent = {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = it)
                    ) {
                        items(Role.entries) { role ->
                            AssistChip(
                                leadingIcon = {
                                    if (role == settings.role) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.done),
                                            contentDescription = stringResource(R.string.this_is_user_role),
                                            tint = Theme.colors.oppositeTheme
                                        )
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 3.dp),
                                onClick = {
                                    viewModel.saveSettingsToSharedPreferences { settings ->
                                        settings.copy(
                                            role = role
                                        ).let { s ->
                                            if (role == Role.Teacher) s.copy(
                                                subgroup = null,
                                                groupId = null
                                            ) else s.copy(teacherName = null)
                                        }
                                    }
                                    viewModel.sync()
                                },
                                label = { TextForThisTheme(text = stringResource(role.resourceName)) }
                            )
                        }

                    }
                }
            )

            if (groupList.isNotEmpty() && settings.role == Role.Student) {
                CardOfSettings(
                    text = stringResource(R.string.group),
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.group),
                            contentDescription = stringResource(R.string.group),
                            tint = it.suitableColor()
                        )
                    },
                    onClick = { isGroupChanging = !isGroupChanging },
                    additionalContentIsVisible = isGroupChanging,
                    additionalContent = {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = it)
                        ) {
                            items(groupList) { group ->

                                AssistChip(
                                    leadingIcon = {
                                        if (group.id == settings.groupId) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.done),
                                                contentDescription = stringResource(R.string.this_is_user_group),
                                                tint = Theme.colors.oppositeTheme
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    onClick = {
                                        viewModel.saveSettingsToSharedPreferences { settings ->
                                            settings.copy(
                                                groupId = if (settings.groupId != group.id) group.id else null
                                            )
                                        }
                                        viewModel.sync()
                                    },
                                    label = { TextForThisTheme(text = group.groupCourseString()) }
                                )
                            }
                        }
                    }
                )
            }

            if (subgroupList.isNotEmpty() && settings.role == Role.Student) {
                CardOfSettings(
                    text = stringResource(R.string.subgroup),
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.subgroup),
                            contentDescription = stringResource(R.string.subgroup),
                            tint = it.suitableColor()
                        )
                    },
                    onClick = { isSubGroupChanging = !isSubGroupChanging },
                    additionalContentIsVisible = isSubGroupChanging,
                    additionalContent = {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = it)
                        ) {
                            items(subgroupList) { subgroup ->
                                AssistChip(
                                    leadingIcon = {
                                        if (subgroup == settings.subgroup) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.done),
                                                contentDescription = stringResource(R.string.this_is_user_subgroup),
                                                tint = Theme.colors.oppositeTheme
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    onClick = {
                                        viewModel.saveSettingsToSharedPreferences { settings ->
                                            settings.copy(
                                                subgroup = if (settings.subgroup != subgroup) subgroup else null
                                            )
                                        }
                                    },
                                    label = { TextForThisTheme(text = subgroup) }
                                )
                            }
                        }
                    }
                )
            }

            if (teachersList.isNotEmpty() && settings.role == Role.Teacher) {
                CardOfSettings(
                    text = stringResource(R.string.teacher),
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.teacher),
                            contentDescription = stringResource(R.string.icon_teacher),
                            tint = it.suitableColor()
                        )
                    },
                    onClick = { isTeacherChanging = !isTeacherChanging },
                    additionalContentIsVisible = isTeacherChanging,
                    additionalContent = {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = it)
                        ) {
                            items(teachersList) { teacher ->
                                AssistChip(
                                    leadingIcon = {
                                        if (teacher.name == settings.teacherName) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.done),
                                                contentDescription = stringResource(R.string.selected_teacher),
                                                tint = Theme.colors.oppositeTheme
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    onClick = {
                                        viewModel.saveSettingsToSharedPreferences { settings ->
                                            settings.copy(
                                                teacherName = if (settings.teacherName != teacher.name) teacher.name else null
                                            )
                                        }
                                        viewModel.sync()
                                    },
                                    label = { TextForThisTheme(text = teacher.name) }
                                )
                            }
                        }
                    }
                )
            }

            CardOfSettings(
                text = stringResource(id = R.string.features),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.tune),
                        contentDescription = stringResource(R.string.features),
                        tint = it.suitableColor()
                    )
                },
                onClick = { isFeaturesEditable = !isFeaturesEditable },
                additionalContentIsVisible = isFeaturesEditable
            ) {
                FeatureOfSettings(
                    onClick = {
                        viewModel.saveSettingsToSharedPreferences { settings ->
                            settings.copy(notificationsAboutLesson = !settings.notificationsAboutLesson)
                        }
                    },
                    padding = it,
                    text = stringResource(R.string.notification_about_lesson_before_time),
                    checked = settings.notificationsAboutLesson
                )
                FeatureOfSettings(
                    onClick = {
                        viewModel.saveSettingsToSharedPreferences { settings ->
                            settings.copy(
                                notesAboutLesson = !settings.notesAboutLesson
                            )
                        }
                    },
                    padding = it,
                    text = stringResource(R.string.note),
                    checked = settings.notesAboutLesson
                )
            }

            TextForThisTheme(
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.CenterHorizontally),
                fontSize = FontSize.big22,
                text = stringResource(R.string.interface_str)
            )

            CardOfSettings(
                text = stringResource(R.string.interface_color),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.palette),
                        contentDescription = stringResource(R.string.change_color_of_interface),
                        tint = it.suitableColor()
                    )
                },
                onClick = { colorIsEditable = true }
            )

            CardOfSettings(
                text = stringResource(R.string.cats_on_ui),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.cat),
                        contentDescription = stringResource(R.string.cats_in_interface),
                        tint = it.suitableColor()
                    )
                },
                onClick = { catsOnUIIsChanging = !catsOnUIIsChanging },
                additionalContentIsVisible = catsOnUIIsChanging,
                additionalContent = {
                    Column {
                        FeatureOfSettings(
                            onClick = {
                                viewModel.saveSettingsToSharedPreferences { settings ->
                                    settings.copy(weekendCat = !settings.weekendCat)
                                }
                            },
                            padding = it,
                            text = stringResource(R.string.weekend_cat),
                            checked = settings.weekendCat
                        )
                        FeatureOfSettings(
                            onClick = {
                                viewModel.saveSettingsToSharedPreferences { settings ->
                                    settings.copy(catInSettings = !settings.catInSettings)
                                }
                            },
                            padding = it,
                            text = stringResource(R.string.cat_in_settings),
                            checked = settings.catInSettings
                        )
                    }
                }
            )

            TextForThisTheme(
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.CenterHorizontally),
                fontSize = FontSize.big22,
                text = stringResource(R.string.contacts)
            )

            CardOfSettings(
                text = stringResource(R.string.code),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.terminal),
                        contentDescription = stringResource(R.string.view_code),
                        tint = it.suitableColor()
                    )
                },
                onClick = { context.openLink(link = Link.CODE) }
            )

            CardOfSettings(
                text = stringResource(R.string.report_a_bug),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.bug_report),
                        contentDescription = stringResource(R.string.report_a_bug),
                        tint = it.suitableColor()
                    )
                }, onClick = {
                    context.sendEmail(email = Link.EMAIL)
                })
            TextForThisTheme(
                modifier = Modifier
                    .padding(10.dp)
                    .padding(bottom = 20.dp)
                    .align(Alignment.End),
                fontSize = FontSize.small17,
                text = "${stringResource(R.string.version)} ${LocalContext.current.getVersionName()}"
            )
        }
    }
}