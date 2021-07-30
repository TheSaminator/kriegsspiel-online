import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.hasClass
import kotlinx.dom.removeClass
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

sealed class Popup<T> {
	protected abstract fun TagConsumer<*>.render(context: CoroutineContext, callback: (T) -> Unit)
	private fun renderInto(consumer: TagConsumer<*>, context: CoroutineContext, callback: (T) -> Unit) {
		consumer.render(context, callback)
	}
	
	suspend fun display(): T {
		while (!popup.hasClass("hide"))
			delay(100L)
		
		popupBox.clear()
		
		return suspendCancellableCoroutine { continuation ->
			popupBox.append {
				renderInto(this, continuation.context) {
					hide()
					continuation.resume(it)
				}
			}
			
			continuation.invokeOnCancellation {
				hide()
			}
			
			show()
		}
	}
	
	companion object {
		private val popup by lazy {
			document.getElementById("popup").unsafeCast<HTMLDivElement>()
		}
		
		private val popupBox by lazy {
			popup.firstElementChild.unsafeCast<HTMLDivElement>()
		}
		
		private fun show() {
			popup.removeClass("hide")
		}
		
		private fun hide() {
			popup.addClass("hide")
		}
	}
	
	object MainMenu : Popup<MainMenuAction>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (MainMenuAction) -> Unit) {
			p {
				style = "text-align: center"
				
				strong { +"Kriegsspiel Online" }
				br
				em { +"the web-based wargame" }
			}
			
			div(classes = "button-set col") {
				a(href = "#") {
					+"Host Game"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(MainMenuAction.Play(GameServerSide.HOST))
					}
				}
				
				a(href = "#") {
					+"Join Game"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(MainMenuAction.Play(GameServerSide.GUEST))
					}
				}
				
				a(href = "#") {
					+"View Kriegspedia"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(MainMenuAction.ViewKriegspedia)
					}
				}
			}
		}
	}
	
	object ChooseNameScreen : Popup<String?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (String?) -> Unit) {
			val nameGameId = "name-game-id"
			val nameGameErrorId = "name-game-error"
			
			p {
				+"Please enter your player name below:"
			}
			
			input(InputType.text) {
				id = nameGameId
			}
			
			p(classes = "error") {
				id = nameGameErrorId
			}
			
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
				a(href = "#") {
					+"Continue"
					onClickFunction = { e ->
						e.preventDefault()
						val nameGameInput = document.getElementById(nameGameId).unsafeCast<HTMLInputElement>()
						
						val gameName = nameGameInput.value
						if (gameName.isBlank()) {
							val hostGameError = document.getElementById(nameGameErrorId).unsafeCast<HTMLSpanElement>()
							hostGameError.innerHTML = "You must enter a player name."
						} else {
							callback(gameName)
						}
					}
				}
			}
		}
	}
	
	class HostScreen(val offerId: String) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Boolean) -> Unit) {
			p {
				+"Your game ID is "
				code {
					+"${playerName!!} ($offerId)"
				}
				+". Copy it and send it to the person with whom you want to start a game. Once you have shared the ID, click Continue to wait for a connection."
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(false)
					}
				}
				a(href = "#") {
					+"Continue"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(true)
					}
				}
			}
		}
	}
	
	class JoinScreen(val sessions: List<WebRTCOpenSession>) : Popup<WebRTCOpenSession?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (WebRTCOpenSession?) -> Unit) {
			val joinGameId = "join-game-id"
			val joinGameErrorId = "join-game-error"
			
			p {
				+"To join a game as ${playerName!!}, please select the game ID from below:"
				
				select {
					id = joinGameId
					
					option {
						value = ""
					}
					
					sessions.forEach { sess ->
						option {
							value = sess.id
							+"${sess.name} (${sess.id})"
						}
					}
				}
				
				span(classes = "error") {
					id = joinGameErrorId
				}
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
				a(href = "#") {
					+"Connect"
					onClickFunction = { e ->
						e.preventDefault()
						val joinGameInput = document.getElementById(joinGameId).unsafeCast<HTMLSelectElement>()
						
						val gameId = joinGameInput.value
						if (gameId.isBlank()) {
							val joinGameError = document.getElementById(joinGameErrorId).unsafeCast<HTMLSpanElement>()
							joinGameError.innerHTML = "You must choose a Game ID"
						} else {
							callback(sessions.singleOrNull { it.id == gameId })
						}
					}
				}
			}
		}
	}
	
	class YesNoDialogue(val acceptText: String = "Accept", val rejectText: String = "Reject", val describeRequest: P.() -> Unit) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Boolean) -> Unit) {
			p {
				describeRequest()
			}
			
			div(classes = "button-set row") {
				a(href = "#") {
					+rejectText
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(false)
					}
				}
				a(href = "#") {
					+acceptText
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(true)
					}
				}
			}
		}
	}
	
	class NameableChoice<T>(val headerText: String, val values: List<T>, val getName: (T) -> String) : Popup<T>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (T) -> Unit) {
			p {
				style = "text-align: center"
				+headerText
			}
			
			div(classes = "button-set col") {
				values.forEach { value ->
					a(href = "#") {
						+getName(value)
						onClickFunction = { e ->
							e.preventDefault()
							
							callback(value)
						}
					}
				}
			}
		}
	}
	
	class LoadingScreen(val loadingText: String, val loadAction: suspend () -> Unit) : Popup<Unit>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Unit) -> Unit) {
			p {
				style = "text-align: center"
				
				+loadingText
			}
			
			GameScope.launch(context) {
				loadAction()
				callback(Unit)
			}
		}
	}
	
	class TryLoadingScreen(val loadingText: String, val successText: String, val failureText: String, val loadAction: suspend () -> Boolean) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Boolean) -> Unit) {
			val nextButtonId = "next-button"
			div(classes = "button-set row") {
				a(href = "#") {
					id = nextButtonId
					+loadingText
					onClickFunction = { e ->
						e.preventDefault()
					}
				}
			}
			
			GameScope.launch(context) {
				val result = loadAction()
				val nextButton = document.getElementById(nextButtonId).unsafeCast<HTMLAnchorElement>()
				
				nextButton.textContent = if (result) successText else failureText
				nextButton.awaitEvent("click", true)
				
				callback(result)
			}
		}
	}
	
	class Message(val message: String, val centerMessage: Boolean, val closeButton: String) : Popup<Unit>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Unit) -> Unit) {
			p {
				if (centerMessage)
					style = "text-align: center"
				
				+message
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+closeButton
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(Unit)
					}
				}
			}
		}
	}
	
	class UncloseableMessage(val message: String, val centerMessage: Boolean) : Popup<Nothing>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Nothing) -> Unit) {
			p {
				if (centerMessage)
					style = "text-align: center"
				
				+message
			}
		}
	}
	
	object KriegspediaStart : Popup<BattleType?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (BattleType?) -> Unit) {
			p {
				style = "text-align: center"
				
				+"Kriegspedia - Start"
			}
			
			div(classes = "button-set col") {
				BattleType.values().forEach { type ->
					a(href = "#") {
						+type.displayName
						onClickFunction = { e ->
							e.preventDefault()
							
							callback(type)
						}
					}
				}
				
				a(href = "#") {
					+"Exit Kriegspedia"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
			}
		}
	}
	
	class KriegspediaIndex(val type: BattleType) : Popup<KriegspediaSection?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (KriegspediaSection?) -> Unit) {
			p {
				style = "text-align: center"
				
				+"${type.displayName} - Index"
			}
			
			div(classes = "button-set col") {
				a(href = "#") {
					+"Mechanics"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(KriegspediaSection.MECHANICS)
					}
				}
				
				a(href = "#") {
					+"Pieces"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(KriegspediaSection.PIECES)
					}
				}
				
				a(href = "#") {
					+"Terrains"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(KriegspediaSection.TERRAINS)
					}
				}
				
				a(href = "#") {
					+"Back"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
			}
		}
	}
	
	class KriegspediaPieceList(val type: BattleType) : Popup<PieceType?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (PieceType?) -> Unit) {
			p {
				style = "text-align: center"
				
				+"${type.displayName} - Pieces"
			}
			
			div(classes = "button-set col") {
				PieceType.values().filter { it.requiredBattleType == type }.forEach { type ->
					a(href = "#") {
						+(type.displayName + type.factionSkin?.let { " (${it.displayName})" }.orEmpty())
						onClickFunction = { e ->
							e.preventDefault()
							
							callback(type)
						}
					}
				}
				
				a(href = "#") {
					+"Back"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
			}
		}
	}
	
	class KriegspediaTerrainList(val type: BattleType) : Popup<TerrainType?>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (TerrainType?) -> Unit) {
			p {
				style = "text-align: center"
				
				+"${type.displayName} - Terrains"
			}
			
			div(classes = "button-set col") {
				TerrainType.values().filter { it.requiredBattleType == type }.forEach { type ->
					a(href = "#") {
						+type.displayName
						onClickFunction = { e ->
							e.preventDefault()
							
							callback(type)
						}
					}
				}
				
				a(href = "#") {
					+"Back"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
			}
		}
	}
	
	class KriegspediaExplanation(val buildExpo: P.() -> Unit) : Popup<Unit>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Unit) -> Unit) {
			p { buildExpo() }
			
			div(classes = "button-set row") {
				a(href = "#") {
					+"Back"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(Unit)
					}
				}
			}
		}
	}
	
	class KriegspediaDataTable(val buildTable: TABLE.() -> Unit) : Popup<Unit>() {
		override fun TagConsumer<*>.render(context: CoroutineContext, callback: (Unit) -> Unit) {
			table { buildTable() }
			
			div(classes = "button-set row") {
				a(href = "#") {
					+"Back"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(Unit)
					}
				}
			}
		}
	}
}
